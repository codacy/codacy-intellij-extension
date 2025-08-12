package com.codacy.intellij.plugin.services.agent

import com.codacy.intellij.plugin.services.agent.model.RepositoryParams
import com.codacy.intellij.plugin.services.cli.CodacyCliService.CodacyCliState
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.GitRemoteParser
import com.codacy.intellij.plugin.services.git.GitProvider
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service
class AiAgentService() {

    lateinit var aiAgent: AiAgent
    lateinit var project: Project

    //TODO change to Provider class
    lateinit var provider: String
    lateinit var organization: String
    lateinit var repository: String
    lateinit var rootPath: String

    private val config = Config.instance
    private var accountToken = config.storedApiToken

    private val cliStateListeners = mutableListOf<() -> Unit>()
    private var isServiceInstantiated: Boolean = false

    //TODO move down
    fun updateWidgetState(codacyCliState: CodacyCliState) {
        this.codacyCliState = codacyCliState
        cliStateListeners.forEach {
            it()
        }
    }

    companion object {
        fun getService(project: Project): AiAgentService {
            val gitProvider = GitProvider.getRepository(project)
                ?: throw IllegalStateException("No Git provider found for the project")

            val remote = gitProvider.remotes.firstOrNull()
                ?: throw IllegalStateException("No remote found in the Git repository")

            val gitInfo = GitRemoteParser.parseGitRemote(remote.firstUrl!!)

            val service = project.getService(AiAgentService::class.java)

            //NOTE: Hardcoded as we currently only support Junie
            // Later we can detect installed plugins with the helper functions and prompt user which to use
            val aiAgent = AiAgent.JUNIE
            service.initService(gitInfo.provider, gitInfo.organization, gitInfo.repository, project, aiAgent)

            return service
        }
    }


    //TODO maybe dont bother and call directly
    fun installGuidelines(params: RepositoryParams?) {
        aiAgent.installGuidelines(project, params)
    }

    //TODO maybe dont bother and call directly
    fun createOrUpdateMcpConfiguration() {
        aiAgent.createOrUpdateConfiguration()
    }


    private fun initService(
        provider: String,
        organization: String,
        repository: String,
        project: Project,
        aiAgent: AiAgent
    ) {

        if (!this.isServiceInstantiated) {
            this.provider = provider
            this.organization = organization
            this.repository = repository
            this.project = project
            this.rootPath = project.basePath!!//TODO, include WSL //cliBehaviour.rootPath(project)
            this.aiAgent = aiAgent

            aiAgent.mcpAiAgentState = AiAgent.AiAgentState.NOT_INSTALLED
            aiAgent.guidelinesAiAgentState = AiAgent.AiAgentState.NOT_INSTALLED
            aiAgent.pluginState = AiAgent.AiAgentPluginState.NOT_INSTALLED

            if (aiAgent.isMcpInstalled(project))
                aiAgent.mcpAiAgentState = AiAgent.AiAgentState.INSTALLED

            if (aiAgent.isGuidelinesInstalled(project))
                aiAgent.guidelinesAiAgentState = AiAgent.AiAgentState.INSTALLED

            if (aiAgent.isPluginInstalled())
                aiAgent.pluginState = AiAgent.AiAgentPluginState.INSTALLED

            if (aiAgent.isPluginEnabled())
                aiAgent.pluginState = AiAgent.AiAgentPluginState.ENABLED

            this.isServiceInstantiated = true
        }
    }

    private fun isNodePresent(): Boolean =
        ProcessBuilder("node", "--version")
            .start()
            .waitFor() == 0
}
