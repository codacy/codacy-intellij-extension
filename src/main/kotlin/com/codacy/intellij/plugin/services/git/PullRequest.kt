package com.codacy.intellij.plugin.services.git

import com.codacy.intellij.plugin.services.api.Api
import com.codacy.intellij.plugin.services.api.models.*
import com.codacy.intellij.plugin.services.common.TimeoutManager
import com.codacy.intellij.plugin.services.git.RepositoryManager.RepositoryManagerState.*
import kotlinx.coroutines.*
import com.intellij.notification.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

const val MAX_IN_MEMORY_ITEMS: Int = 300
const val PR_REFRESH_TIME: Long = 1 * 60 * 1000

@Service(Service.Level.PROJECT)
class PullRequest(
    private val project: Project
) {
    var prWithAnalysis: GetRepositoryPullRequestResponse? = null
    private var headCommit: String? = null
    private var baseCommit: String? = null
    var issues: List<IssueDetails> = listOf()
    var files: List<FileDetails> = mutableListOf()
    var gates: QualitySettingsData? = null
    private val refreshTimeout: TimeoutManager = TimeoutManager()
    private val api = Api()
    private var repositoryManager: RepositoryManager? = null

    fun init(
        pr: GetRepositoryPullRequestResponse,
        rm: RepositoryManager): PullRequest {
        prWithAnalysis = pr
        repositoryManager = rm
        issues = listOf()
        files = mutableListOf()
        refresh(true)
        return this
    }

    private fun ensureRepository(): RepositoryData {
        if (repositoryManager!!.state !== Loaded || repositoryManager!!.repository == null) {
            throw IllegalStateException("Forbidden call")
        }
        return repositoryManager!!.repository!!
    }

    suspend fun fetchMetadata() {
        val repo = ensureRepository()
        val prNumber = prWithAnalysis?.pullRequest?.number ?: return
        prWithAnalysis = api.getRepositoryPullRequest(
            repo.provider,
            repo.owner,
            repo.name,
            prNumber
        )
    }

    suspend fun fetchQualityGates() {
        val repo = ensureRepository()
        gates = api.getPullRequestQualitySettings(
            repo.provider,
            repo.owner,
            repo.name
        ).data
    }

    suspend fun fetchIssues() {
        val repo = ensureRepository()
        val prNumber = prWithAnalysis?.pullRequest?.number ?: return
        issues = listOf()

        issues = api.listPullRequestIssues(
            repo.provider,
            repo.owner,
            repo.name,
            prNumber
        ).data

        val prCoverageReports = api.getPullRequestCoverageReports(repo.provider, repo.owner, repo.name, prNumber)
        headCommit = prCoverageReports.data.headCommit.commitSha
        baseCommit = prCoverageReports.data.commonAncestorCommit.commitSha
    }

    suspend fun fetchFiles() {
        val repo = ensureRepository()
        var nextCursor: String? = null
        files = mutableListOf()

        do {
            val filesResponse = api.listPullRequestFiles(
                repo.provider,
                repo.owner,
                repo.name,
                prWithAnalysis?.pullRequest?.number ?: return,
                nextCursor ?: "1"
            )

            (files as MutableList<FileDetails>).addAll(filesResponse.data)
            nextCursor = filesResponse.pagination.cursor
        } while (nextCursor != null && files.size < MAX_IN_MEMORY_ITEMS)
    }

    fun showAnalysisNotification() {
        if (prWithAnalysis!!.isUpToStandards == true) {
            if (
                (prWithAnalysis!!.newIssues) > 0 ||
                (prWithAnalysis!!.deltaClonesCount) > 0 ||
                (prWithAnalysis!!.deltaComplexity) > 0 ||
                (prWithAnalysis!!.coverage.deltaCoverage != null && prWithAnalysis!!.coverage.deltaCoverage!! < -0.05) ||
                (prWithAnalysis!!.coverage.diffCoverage?.value != null && prWithAnalysis!!.coverage.diffCoverage?.value!! < 50)
            ) {
                showNotification(
                    "Your pull request is up to standards! Check how to improve your code even more",
                    NotificationType.WARNING
                ) {
                    openSummary()
                }
            } else {
                showNotification("Your pull request is up to standards!", NotificationType.INFORMATION)
            }
        } else {
            showNotification("Your pull request is not up to standards", NotificationType.WARNING) {
                openSummary()
            }
        }
    }

    private fun showNotification(content: String, type: NotificationType, onClick: (() -> Unit)? = null) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification(content, type)

        onClick?.let {
            val action = NotificationAction.createSimple("Show details") {
                it.invoke()
            }
            notification.addAction(action)
        }

        notification.notify(project)
    }

    private fun openSummary() {
        ToolWindowManager.getInstance(project).getToolWindow("Codacy")?.activate(null)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun refresh(avoidMetadataFetch: Boolean = false) {
        ProgressManager.getInstance().run(object: Task.Backgroundable(project, "Refreshing pull request", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                GlobalScope.launch {
                    val wasAnalysing = prWithAnalysis!!.isAnalysing

                    if (!avoidMetadataFetch) fetchMetadata()

                    fetchQualityGates()
                    fetchIssues()
                    fetchFiles()

                    repositoryManager!!.notifyDidUpdatePullRequest()

                    val currentHeadCommitSHA: String = GitProvider.getHeadCommitSHA(project)!!
                    val currentHeadAhead: Boolean = GitProvider.isHeadAhead(project)

                    if (prWithAnalysis!!.isAnalysing || (headCommit != currentHeadCommitSHA && !currentHeadAhead)
                    ) {
                        refreshTimeout.clearTimeout()
                        refreshTimeout.startTimeout(PR_REFRESH_TIME) {
                            refresh()
                        }
                    } else if (wasAnalysing) {
                        showAnalysisNotification()
                    }
                }
            }
        })
    }

    val meta: PullRequestMeta?
        get() = prWithAnalysis?.pullRequest?.let {
            PullRequestMeta(
                it,
                headCommitSHA = headCommit,
                commonAncestorCommitSHA = baseCommit
            )
        }

    data class PullRequestMeta(
        val pullRequest: PullRequestData,
        val headCommitSHA: String?,
        val commonAncestorCommitSHA: String?
    )

}
