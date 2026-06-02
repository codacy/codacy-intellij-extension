package com.codacy.intellij.plugin.listeners

import com.codacy.intellij.plugin.services.agent.AiAgentService
import com.codacy.intellij.plugin.services.agent.model.Provider
import com.codacy.intellij.plugin.services.api.Api
import com.codacy.intellij.plugin.services.cli.CodacyCliService
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Logger
import com.codacy.intellij.plugin.services.common.GitRemoteParser
import com.codacy.intellij.plugin.services.common.IconUtils
import com.codacy.intellij.plugin.services.git.GitProvider
import com.codacy.intellij.plugin.services.git.RepositoryManager
import com.codacy.intellij.plugin.telemetry.ExtensionInstalledEvent
import com.codacy.intellij.plugin.telemetry.Telemetry
import com.codacy.intellij.plugin.views.invalidateAnalysisCacheForHash
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.*

class StartupListener : StartupActivity {

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 5000L
        private val activeScopes = java.util.Collections.synchronizedList(mutableListOf<CoroutineScope>())

        fun cancelAllScopes() {
            synchronized(activeScopes) {
                activeScopes.forEach { it.cancel() }
                activeScopes.clear()
            }
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

            if (gitResult == null) {
                Logger.info("No git remote found, initializing services without remote info.")
                CodacyCliService.getServiceWithoutRemote(project)
                AiAgentService.getServiceWithoutRemote(project)
            } else {
                val repositoryManager = gitResult.first
                val gitInfo = gitResult.second

                CodacyCliService.getService(
                    Provider.fromString(gitInfo.provider), gitInfo.organization, gitInfo.repository, project,
                )

                AiAgentService.getService(project)

                scope.launch {
                    try {
                        service<Api>().listTools()
                    } catch (e: Exception) {
                        Logger.warn("Failed to load tools: ${e.message}")
                    }
                }

                if (repository.currentBranch != null) {
                    scope.launch {
                        repositoryManager.handleStateChange()
                    }
                } else {
                    repositoryManager.notifyDidChangeConfig()
                }
            }
        })

        // Perform initial startup initialization without waiting for a git change event
        val gitRepository = GitProvider.getRepository(project)
        if (gitRepository != null) {
            val gitResult = initializeGit(project, scope)
            if (gitResult == null) {
                CodacyCliService.getServiceWithoutRemote(project)
                AiAgentService.getServiceWithoutRemote(project)
            } else {
                val (_, gitInfo) = gitResult
                CodacyCliService.getService(Provider.fromString(gitInfo.provider), gitInfo.organization, gitInfo.repository, project)
                AiAgentService.getService(project)
            }
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
                triggerAnalysisForSavedFiles(project, events)
            }
        })
    }

    private fun initializeGit(project: Project, scope: CoroutineScope): Pair<RepositoryManager, GitRemoteParser.GitRemoteInfo>? {
        val gitRepository = GitProvider.getRepository(project)
        val repositoryManager = project.service<RepositoryManager>()
        if (gitRepository != null && repositoryManager.currentRepository != gitRepository)
            scope.launch { repositoryManager.open(gitRepository) }

        val remote = gitRepository?.remotes?.firstOrNull() ?: return null
        val gitInfo = GitRemoteParser.parseGitRemote(remote.firstUrl!!)

        return Pair(repositoryManager, gitInfo)
    }

    // Re-triggering services helps with keeping the state of the plugin consistent
    // E.g. if the user deletes CLI.sh, re-initialization of the project will check for the
    // presence of the CLI and mark its state as not-installed
    private fun onProjectFileSystemChange(project: Project, events: List<VFileEvent>) {
        if (GitProvider.getRepository(project) == null) return
        try {
            CodacyCliService.getService(project)
            AiAgentService.getService(project)
        } catch (e: IllegalStateException) {
            Logger.info("Git provider or remote not available.")
        }
    }

    private fun triggerAnalysisForSavedFiles(project: Project, events: List<VFileEvent>) {
        val basePath = project.basePath ?: return
        val savedFiles = events
            .filterIsInstance<VFileContentChangeEvent>()
            .mapNotNull { it.file }
            .filter { vf ->
                vf.isValid &&
                    !vf.isDirectory &&
                    vf.path.startsWith(basePath) &&
                    !vf.path.contains("/.codacy/")
            }

        if (savedFiles.isEmpty()) return

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val psiManager = PsiManager.getInstance(project)
            val documentManager = PsiDocumentManager.getInstance(project)
            val daemon = DaemonCodeAnalyzer.getInstance(project)
            for (vf in savedFiles) {
                val psiFile = psiManager.findFile(vf) ?: continue
                // cli.analyze reads from disk, so the cached entry (keyed by document hash)
                // reflects the pre-save disk content. Drop it so the next pass re-runs the CLI.
                documentManager.getDocument(psiFile)?.let { doc ->
                    invalidateAnalysisCacheForHash(doc.text.hashCode())
                }
                daemon.restart(psiFile)
            }
        }
    }
}
