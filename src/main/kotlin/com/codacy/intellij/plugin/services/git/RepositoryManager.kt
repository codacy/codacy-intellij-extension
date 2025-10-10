package com.codacy.intellij.plugin.services.git

import com.codacy.intellij.plugin.services.api.Api
import com.codacy.intellij.plugin.services.api.models.Branch
import com.codacy.intellij.plugin.services.api.models.RepositoryData
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.GitRemoteParser
import com.codacy.intellij.plugin.services.common.Logger
import com.codacy.intellij.plugin.services.common.TimeoutManager
import com.codacy.intellij.plugin.telemetry.BranchStateChangeEvent
import com.codacy.intellij.plugin.telemetry.PullRequestStateChangeEvent
import com.codacy.intellij.plugin.telemetry.RepositoryStateChangeEvent
import com.codacy.intellij.plugin.telemetry.Telemetry
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import kotlinx.coroutines.*

const val LOAD_RETRY_TIME: Long = 2 * 60 * 1000
const val MAX_LOAD_ATTEMPTS: Int = 5

@Service(Service.Level.PROJECT)
class RepositoryManager(private val project: Project) {

    enum class RepositoryManagerState {
        Initializing,
        NeedsAuthentication,
        NoGitRepository,
        Loaded,
        NoRepository
    }

    enum class PullRequestState {
        NoPullRequest, Loaded
    }

    enum class BranchState {
        OnPullRequestBranch, OnAnalysedBranch, OnAnalysedBranchOutdated, OnUnknownBranch
    }

    var currentRepository: GitRepository? = null
    var repository: RepositoryData? = null
    var state: RepositoryManagerState = RepositoryManagerState.Initializing
    private var branch: String? = null
    var pullRequest: PullRequest? = null
    var prState: PullRequestState = PullRequestState.NoPullRequest
    private var loadAttempts: Int = 0
    private val loadTimeout: TimeoutManager = TimeoutManager()
    private val refreshTimeout: TimeoutManager = TimeoutManager()
    private val api = Api()
    private val config = Config()
    private var pullRequestInstance: PullRequest? = null

    // Branch state management
    private var enabledBranches: List<Branch> = emptyList()
    private var branchState: BranchState = BranchState.OnUnknownBranch
    private var lastBranchState: BranchState? = null

    private var onDidUpdatePullRequestListeners = mutableListOf<() -> Unit>()
    private var onDidLoadRepositoryListeners = mutableListOf<() -> Unit>()
    private var onDidChangeStateListeners = mutableListOf<() -> Unit>()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun open(gitRepository: GitRepository) {
        config.init()
        if (config.storedApiToken.isNullOrBlank()) {
            setNewState(RepositoryManagerState.NeedsAuthentication)
            return
        }

        if (currentRepository != gitRepository) {
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Opening repository", false) {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    GlobalScope.launch {
                        currentRepository = gitRepository
                        try {
                            val remoteUrl = gitRepository.remotes.firstOrNull()?.pushUrls?.firstOrNull()

                            if (gitRepository.currentBranchName.isNullOrBlank()) {
                                setNewState(RepositoryManagerState.Initializing)
                            } else {
                                if (remoteUrl.isNullOrEmpty()) {
                                    setNewState(RepositoryManagerState.NoGitRepository)
                                    Logger.error("No remote found")
                                    return@launch
                                }

                                val repo = GitRemoteParser.parseGitRemote(remoteUrl)
                                val (data) = api.getRepository(repo.provider, repo.organization, repo.repository)

                                repository = data

                                // Fetch enabled branches
                                try {
                                    val branchesResponse = api.listRepositoryBranches(repo.provider, repo.organization, repo.repository, true)
                                    enabledBranches = branchesResponse.data
                                    Logger.info("Fetched ${enabledBranches.size} enabled branches")
                                } catch (e: Exception) {
                                    Logger.error("Failed to fetch enabled branches: ${e.message}")
                                    enabledBranches = emptyList()
                                }

                                setNewState(RepositoryManagerState.Loaded)
                                notifyDidLoadRepository()
                                loadPullRequest()
                            }
                        } catch (e: Error) {
//                            TODO("error type (ApiError)")
                            Logger.error("Failed to parse Git remote: ${e.message}")
                            setNewState(RepositoryManagerState.NoRepository)
                            return@launch
                        }
                    }
                }
            })
        }
    }

    suspend fun handleStateChange() {
        val currentHead = currentRepository?.currentBranch?.name
        if (currentHead != branch) {
            Logger.info("Branch changed: ${currentHead}, looking for pull request...")
            branch = currentHead
            pullRequest = null
            notifyDidUpdatePullRequest()
            setNewPullRequestState(PullRequestState.NoPullRequest)

            loadPullRequest()
        } else {
            val currentHeadCommitSHA = GitProvider.getHeadCommitSHA(project)
            val currentHeadAhead: Boolean = GitProvider.isHeadAhead(project)
            if (!currentHeadCommitSHA.isNullOrEmpty() && isPrLoadedAndNotCheckedOut(pullRequest, prState, currentHeadCommitSHA, currentHeadAhead)) {
                if (refreshTimeout.isTimeoutRunning()) {
                    refreshTimeout.clearTimeout()
                }
                refreshTimeout.startTimeout(10000) {
                    Logger.info("Pushed all local commits, refreshing pull request...")
                    pullRequestInstance!!.refresh()
                }
            }
        }
    }

    private fun isPrLoadedAndNotCheckedOut(
        pullRequest: PullRequest?,
        prState: PullRequestState,
        currentHeadCommitSHA: String,
        currentHeadAhead: Boolean
    ): Boolean =
        pullRequest != null &&
                prState === PullRequestState.Loaded &&
                currentHeadCommitSHA != pullRequest.meta?.headCommitSHA &&
                !currentHeadAhead


    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun loadPullRequest() {
        if (loadTimeout.isTimeoutRunning()) loadTimeout.clearTimeout()
        if (state != RepositoryManagerState.Loaded || repository == null) return

        val repo = repository!!
        branch = currentRepository?.currentBranch?.name
        if (branch.isNullOrBlank()) {
            Logger.warn("No HEAD information found: ${currentRepository?.currentBranch}")
            setNewPullRequestState(PullRequestState.NoPullRequest)
            return
        }

        if (branch == repo.defaultBranch.name) {
            Logger.info("Current branch is the default branch: $branch")
            setNewPullRequestState(PullRequestState.NoPullRequest)
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading pull request", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                GlobalScope.launch {
                    try {
                        val repositoryPullRequests =
                            api.listRepositoryPullRequests(repo.provider, repo.owner, repo.name)
                        val prs = repositoryPullRequests.data
                        val pr = prs.find { it.pullRequest.originBranch == branch }

                        if (pr == null) {
                            Logger.info("No PR found in Codacy for: $branch")
                            setNewPullRequestState(PullRequestState.NoPullRequest)

                            if (loadAttempts < MAX_LOAD_ATTEMPTS) {
                                loadTimeout.startTimeout(LOAD_RETRY_TIME) {
                                    CoroutineScope(Dispatchers.Default).launch {
                                        loadPullRequest()
                                    }
                                }
                                loadAttempts++
                            }
                            return@launch
                        }

                        if (pr.pullRequest.number == pullRequestInstance?.meta?.pullRequest?.number) {
                            pullRequestInstance!!.refresh()
                        } else {
                            pullRequestInstance = project.service<PullRequest>()
                            pullRequest = pullRequestInstance!!.init(pr, this@RepositoryManager)
                            notifyDidUpdatePullRequest()
                        }

                        setNewPullRequestState(PullRequestState.Loaded)
                    } catch (e: Exception) {
                        Logger.error("Error loading pull request: ${e.message}")
                    }
                }
            }
        })
    }

    fun clear() {
        currentRepository = null
        enabledBranches = emptyList()
        branchState = BranchState.OnUnknownBranch
        lastBranchState = null
        setNewState(RepositoryManagerState.NoRepository)
    }

    fun setNewState(newState: RepositoryManagerState) {
        val stateChange: Boolean = newState !== state
        state = newState
        if (stateChange) {
            Telemetry.track(RepositoryStateChangeEvent(newState.name))
//            TODO("execute command setContext")
            notifyDidChangeState()
        }
    }

    fun setNewPullRequestState(newState: PullRequestState) {
        val stateChange: Boolean = newState !== prState
        prState = newState
        if (stateChange) {
            // Track pull request state change telemetry
            Telemetry.track(PullRequestStateChangeEvent(newState.name))
        }
    }

    fun onDidUpdatePullRequest(listener: () -> Unit): Disposable {
        onDidUpdatePullRequestListeners.add(listener)
        return Disposable { onDidUpdatePullRequestListeners.remove(listener) }
    }

    fun onDidLoadRepository(listener: () -> Unit) {
        onDidLoadRepositoryListeners.add(listener)
    }

    fun onDidChangeState(listener: () -> Unit) {
        onDidChangeStateListeners.add(listener)
    }

    fun notifyDidUpdatePullRequest() {
        onDidUpdatePullRequestListeners.forEach { it.invoke() }
    }

    fun notifyDidLoadRepository() {
        onDidLoadRepositoryListeners.forEach { it.invoke() }
    }

    private fun notifyDidChangeState() {
        onDidChangeStateListeners.forEach { it.invoke() }
    }

    fun notifyDidChangeConfig() {
//        TODO: do I need to check the repository? if yes, remove clear()
        clear()
        val gitRepository: GitRepository? = GitProvider.getRepository(project)
        if (gitRepository != null && currentRepository != gitRepository) {
            CoroutineScope(Dispatchers.Default).launch {
                open(gitRepository)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun evaluateBranchState() {
        if (state != RepositoryManagerState.Loaded || repository == null) return

        val currentBranch = currentRepository?.currentBranch?.name
        val repo = repository!!

        // If no HEAD name, set to OnUnknownBranch
        if (currentBranch.isNullOrBlank()) {
            Logger.warn("No HEAD information found: ${currentRepository?.currentBranch}")
            setBranchState(BranchState.OnUnknownBranch)
            return
        }

        // If PR is loaded, set to OnPullRequestBranch
        if (prState == PullRequestState.Loaded) {
            setBranchState(BranchState.OnPullRequestBranch)
            return
        }

        // Check if current branch is in enabled branches
        val isEnabledBranch = enabledBranches.any { it.name == currentBranch }

        if (isEnabledBranch) {
            Logger.info("Current branch is an analyzed branch: $currentBranch")

            // Get the last analyzed commit for this branch
            try {
                val analysisResponse = api.getRepositoryWithAnalysis(repo.provider, repo.owner, repo.name)
                val lastAnalysedCommit = analysisResponse.data.lastAnalysedCommit
                val localHeadCommit = GitProvider.getHeadCommitSHA(project)

                if (localHeadCommit != null && lastAnalysedCommit.sha != localHeadCommit) {
                    Logger.info("Local branch '$currentBranch' is outdated: Local Head ${localHeadCommit.substring(0, 7)} !== Last analysed Head ${lastAnalysedCommit.sha.substring(0, 7)}")
                    setBranchState(BranchState.OnAnalysedBranchOutdated)
                } else {
                    setBranchState(BranchState.OnAnalysedBranch)
                }
            } catch (e: Exception) {
                Logger.error("Failed to get repository analysis: ${e.message}")
                setBranchState(BranchState.OnUnknownBranch)
            }
        } else {
            // Not an enabled branch and no PR loaded
            setBranchState(BranchState.OnUnknownBranch)
        }
    }

    private fun setBranchState(newState: BranchState) {
        if (branchState != newState) {
            val previousState = branchState
            branchState = newState
            lastBranchState = previousState

            // Emit telemetry
            Telemetry.track(BranchStateChangeEvent(newState.name))

            Logger.info("Branch state changed from $previousState to $newState")
        }
    }

}
