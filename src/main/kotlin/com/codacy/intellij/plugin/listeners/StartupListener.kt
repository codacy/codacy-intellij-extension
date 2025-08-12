package com.codacy.intellij.plugin.listeners

import com.codacy.intellij.plugin.services.api.Api
import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.GitRemoteParser
import com.codacy.intellij.plugin.services.common.IconUtils
import com.codacy.intellij.plugin.services.git.GitProvider
import com.codacy.intellij.plugin.services.git.RepositoryManager
import com.codacy.intellij.plugin.services.mcp.AiAgentService
import com.codacy.intellij.plugin.telemetry.Telemetry
import com.codacy.intellij.plugin.telemetry.ExtensionInstalledEvent
import com.codacy.intellij.plugin.views.CodacyCliStatusBarWidgetFactory
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.messages.Topic
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import kotlinx.coroutines.*

//Note: This Class runs AFTER WidgetFactory
class StartupListener : StartupActivity {

    @OptIn(DelicateCoroutinesApi::class)
    override fun runActivity(project: Project) {
        // Check for first installation
        val config = Config.instance
        if (config.state.isFirstRun) {
            config.state.isFirstRun = false
            Telemetry.track(ExtensionInstalledEvent)
        }

        // Preload the codacy icon
        IconUtils.CodacyIcon

        val gitRepository = GitProvider.getRepository(project)
        val repositoryManager = project.service<RepositoryManager>()
        if (gitRepository != null && repositoryManager.currentRepository != gitRepository)
            GlobalScope.launch { repositoryManager.open(gitRepository) }

        val remote = gitRepository?.remotes?.firstOrNull()
            ?: throw IllegalStateException("No remote found in the Git repository")

        val gitInfo = GitRemoteParser.parseGitRemote(remote.firstUrl!!)

        CodacyCli.getService(
            gitInfo.provider, gitInfo.organization, gitInfo.repository, project,
        )

        AiAgentService.getService(project)

        GlobalScope.launch {
            Api().listTools()
        }

        // Plugin listeners
        val connection = project.messageBus.connect()

        connection.subscribe(DynamicPluginListener.TOPIC, PluginStateListener())

        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
            if (repository.currentBranch != null) {
                CoroutineScope(Dispatchers.Default).launch {
                    repositoryManager.handleStateChange()
                }
            } else {
                    repositoryManager.notifyDidChangeConfig()
                }
            })
    }
}
