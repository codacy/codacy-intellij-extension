package com.codacy.intellij.plugin.listeners

import com.codacy.intellij.plugin.services.agent.AiAgentService
import com.codacy.intellij.plugin.services.agent.model.Provider
import com.codacy.intellij.plugin.services.api.Api
import com.codacy.intellij.plugin.services.cli.CodacyCliService
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.GitRemoteParser
import com.codacy.intellij.plugin.services.common.IconUtils
import com.codacy.intellij.plugin.services.git.GitProvider
import com.codacy.intellij.plugin.services.git.RepositoryManager
import com.codacy.intellij.plugin.telemetry.ExtensionInstalledEvent
import com.codacy.intellij.plugin.telemetry.Telemetry
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.*

class StartupListener : StartupActivity {

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 5000L
        private val activeScopes = mutableListOf<CoroutineScope>()

        fun cancelAllScopes() {
            activeScopes.forEach { it.cancel() }
            activeScopes.clear()
        }
    }

    private var lastTriggeredTime = 0L

    override fun runActivity(project: Project) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        activeScopes.add(scope)
        Disposer.register(project, Disposable {
            scope.cancel()
            activeScopes.remove(scope)
        })

        // Check for first installation
        val config = Config.instance
        if (config.state.isFirstRun) {
            config.state.isFirstRun = false
            Telemetry.track(ExtensionInstalledEvent)
        }

        // Preload the codacy icon
        IconUtils.CodacyIcon

        // Plugin listeners
        val connection = project.messageBus.connect()

        connection.subscribe(DynamicPluginListener.TOPIC, PluginStateListener())

        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->


            val gitResult = initializeGit(project, scope)
            val repositoryManager = gitResult.first
            val gitInfo = gitResult.second

            CodacyCliService.getService(
                Provider.fromString(gitInfo.provider), gitInfo.organization, gitInfo.repository, project,
            )

            AiAgentService.getService(project)

            scope.launch {
                service<Api>().listTools()
            }

            if (repository.currentBranch != null) {
                scope.launch {
                    repositoryManager.handleStateChange()
                }
            } else {
                repositoryManager.notifyDidChangeConfig()
            }
        })

        // Perform initial startup initialization without waiting for a git change event
        val gitRepository = GitProvider.getRepository(project)
        if (gitRepository != null) {
            val repositoryManager = project.service<RepositoryManager>()
            scope.launch { repositoryManager.open(gitRepository) }
        }

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            private fun handle(events: List<VFileEvent>) {
                val now = System.currentTimeMillis()
                if (now - lastTriggeredTime >= MIN_REFRESH_INTERVAL_MS) {
                    val basePath = project.basePath
                    if (basePath != null && events.any { it.path.startsWith(basePath) }) {
                        lastTriggeredTime = now
                        onProjectFileSystemChange(project, events)
                    }
                }
            }

            override fun before(events: List<VFileEvent>) {
                handle(events)
            }

            override fun after(events: List<VFileEvent>) {
                handle(events)
            }
        })
    }

    private fun initializeGit(project: Project, scope: CoroutineScope): Pair<RepositoryManager, GitRemoteParser.GitRemoteInfo> {
        val gitRepository = GitProvider.getRepository(project)
        val repositoryManager = project.service<RepositoryManager>()
        if (gitRepository != null && repositoryManager.currentRepository != gitRepository)
            scope.launch { repositoryManager.open(gitRepository) }

        val remote = gitRepository?.remotes?.firstOrNull()
            ?: throw IllegalStateException("No remote found in the Git repository")

        val gitInfo = GitRemoteParser.parseGitRemote(remote.firstUrl!!)

        return Pair(repositoryManager, gitInfo)
    }

    // Re-triggering services helps with keeping the state of the plugin consistent
    // E.g. if the user deletes CLI.sh, re-initialization of the project will check for the
    // presence of the CLI and mark its state as not-installed
    private fun onProjectFileSystemChange(project: Project, events: List<VFileEvent>) {
        CodacyCliService.getService(project)
        AiAgentService.getService(project)
    }
}
