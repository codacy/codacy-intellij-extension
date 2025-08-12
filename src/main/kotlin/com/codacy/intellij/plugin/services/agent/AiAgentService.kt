package com.codacy.intellij.plugin.services.agent

import com.codacy.intellij.plugin.listeners.ServiceState
import com.codacy.intellij.plugin.listeners.WidgetStateListener
import com.codacy.intellij.plugin.services.agent.model.McpConfig
import com.codacy.intellij.plugin.services.agent.model.McpConfigGithubCopilot
import com.codacy.intellij.plugin.services.agent.model.McpConfigJunie
import com.codacy.intellij.plugin.services.agent.model.McpServer
import com.codacy.intellij.plugin.services.agent.model.McpServerGithubCopilot
import com.codacy.intellij.plugin.services.agent.model.McpServerJunie
import com.codacy.intellij.plugin.services.agent.model.RepositoryParams
import com.codacy.intellij.plugin.services.agent.model.RuleScope
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.GitRemoteParser
import com.codacy.intellij.plugin.services.git.GitProvider
import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Service
class AiAgentService() {

    sealed interface AiAgentState {
        object NOT_INSTALLED : AiAgentState
        object INSTALLED : AiAgentState
    }

    sealed interface AiAgentPluginState {
        object NOT_INSTALLED : AiAgentPluginState
        object INSTALLED : AiAgentPluginState
        object ENABLED : AiAgentPluginState
    }

    lateinit var guidelinesAiAgentState: AiAgentState
    lateinit var mcpAiAgentState: AiAgentState
    lateinit var pluginState: AiAgentPluginState

    lateinit var aiAgent: AiAgent
    lateinit var project: Project

    //TODO change to Provider class
    lateinit var provider: String
    lateinit var organization: String
    lateinit var repository: String
    lateinit var rootPath: String

    private val config = Config.instance
    private var accountToken = config.storedApiToken

    private var isServiceInstantiated: Boolean = false

    private var serviceState = ServiceState.STARTING

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
            val aiAgent = AiAgent.JUNIE(
                Paths.get(project.basePath ?: throw RuntimeException("Failed to get project's base path")),
                Paths.get(System.getProperty("user.home") ?: throw RuntimeException("Failed to get user home")),
            )

            service.initService(gitInfo.provider, gitInfo.organization, gitInfo.repository, project, aiAgent)

            return service
        }
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
            this.rootPath = project.basePath!!//TODO, include WSL behaviour, this is also force cast
            this.aiAgent = aiAgent


            setServiceState(ServiceState.RUNNING)

            this.isServiceInstantiated = true
        }

        mcpAiAgentState = AiAgentState.NOT_INSTALLED
        guidelinesAiAgentState = AiAgentState.NOT_INSTALLED
        pluginState = AiAgentPluginState.NOT_INSTALLED

        if (aiAgent.isMcpInstalled(accountToken))
            mcpAiAgentState = AiAgentState.INSTALLED

        if (aiAgent.isGuidelinesInstalled())
            guidelinesAiAgentState = AiAgentState.INSTALLED

        if (aiAgent.isPluginInstalled())
            pluginState = AiAgentPluginState.INSTALLED

        if (aiAgent.isPluginEnabled())
            pluginState = AiAgentPluginState.ENABLED
    }

    fun createOrUpdateMcpConfiguration() {
        val gson = Gson()

        if (!aiAgent.mcpConfigurationPath.exists()) {
            File(aiAgent.mcpConfigurationPath.toString()).mkdirs()
        }

        fun <S : McpServer, C : McpConfig> createOrUpdate(
            makeServer: () -> S,
            parseConfig: (String) -> C?,
            extractServers: (C) -> Map<String, S>,
            buildConfig: (Map<String, S>) -> Any
        ) {
            val server = makeServer()
            val file = File(aiAgent.mcpConfigurationFilePath.toString())

            if (aiAgent.mcpConfigurationFilePath.exists()) {
                val json = file.readText()
                val existing = try {
                    parseConfig(json)
                } catch (_: Exception) {
                    mcpAiAgentState = AiAgentState.NOT_INSTALLED
                    null
                }
                val current = existing?.let { extractServers(it) } ?: emptyMap()
                val updatedMap = current.toMutableMap().apply { this["codacy"] = server }
                val updatedConfig = buildConfig(updatedMap)
                file.writeText(gson.toJson(updatedConfig))
            } else {
                val newConfig = buildConfig(mapOf("codacy" to server))
                file.writeText(gson.toJson(newConfig))
            }
            mcpAiAgentState = AiAgentState.INSTALLED
        }

        when (aiAgent) {
            is AiAgent.JUNIE -> createOrUpdate(
                makeServer = {
                    McpServerJunie(
                        command = "npx",
                        args = listOf("-y", "@codacy/codacy-mcp"),
                        env = mapOf(
                            "CODACY_ACCOUNT_TOKEN" to (accountToken ?: "<YOUR CODACY TOKEN>"),
                        )
                    )
                },
                parseConfig = { json -> gson.fromJson(json, McpConfigJunie::class.java) },
                extractServers = { cfg -> cfg.mcpServers },
                buildConfig = { servers -> McpConfigJunie(mcpServers = servers) }
            )

            is AiAgent.GITHUB_COPILOT -> createOrUpdate(
                makeServer = {
                    McpServerGithubCopilot(
                        type = "stdio",
                        command = "npx",
                        args = listOf("-y", "@codacy/codacy-mcp"),
                        env = mapOf(
                            "CODACY_ACCOUNT_TOKEN" to (accountToken ?: "<YOUR CODACY TOKEN>"),
                        )
                    )
                },
                parseConfig = { json -> gson.fromJson(json, McpConfigGithubCopilot::class.java) },
                extractServers = { cfg -> cfg.servers },
                buildConfig = { servers -> McpConfigGithubCopilot(servers = servers) }
            )
        }
    }

    fun installGuidelines(project: Project, params: RepositoryParams?) {
        if (!Config.instance.state.generateGuidelines) {
            //TODO notification that it wont be ran
            return
        }

        val basePath = project.basePath ?: throw RuntimeException("Project base path is null")

        val newRules = McpRulesTemplate.newRulesTemplate(
            project = project,
            repositoryParams = params,
            excludedScopes = listOf(RuleScope.GUARDRAILS)
        )

        val rulesPath = aiAgent.guidelinesFilePath
        val dirPath = rulesPath.parent

        if (!dirPath.exists()) {
            dirPath.toFile().mkdirs()
        }


        if (!rulesPath.exists()) {
            rulesPath.writeText(
                McpRulesTemplate.convertRulesToMarkdown(newRules)
            )
            McpRulesTemplate.addRulesToGitignore(basePath, rulesPath)
            guidelinesAiAgentState = AiAgentState.INSTALLED
        } else {
            try {
                val existingContent = rulesPath.readText()
                rulesPath.writeText(
                    McpRulesTemplate.convertRulesToMarkdown(newRules, existingContent)
                )
                guidelinesAiAgentState = AiAgentState.INSTALLED
            } catch (e: Exception) {
                guidelinesAiAgentState = AiAgentState.NOT_INSTALLED
                //TODO in vscode there might be parsing error
            }
        }
    }

    fun setServiceState(newServiceState: ServiceState) {
        if (serviceState != newServiceState) {
            serviceState = newServiceState
            project.messageBus
                .syncPublisher(WidgetStateListener.AI_AGENT_TOPIC)
                .stateChanged(serviceState)
        }
    }

    private fun isNodePresent(): Boolean =
        ProcessBuilder("node", "--version")
            .start()
            .waitFor() == 0
}
