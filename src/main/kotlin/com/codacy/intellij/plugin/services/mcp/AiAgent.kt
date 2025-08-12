package com.codacy.intellij.plugin.services.mcp

import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.mcp.model.*
import com.google.gson.Gson
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writeText

//TODO maybe make it sealed class instead
sealed class AiAgent() {

    sealed interface State {
        object NOT_INSTALLED : State
        object INSTALLED : State
        object ENABLED : State
    }

    abstract val pluginId: String
    abstract val mcpConfigurationPath: Path
    abstract val mcpConfigurationFilePath: Path
    abstract var state: State

    //TODO turn to val
    abstract fun guidelinesFilePath(project: Project): Path

    fun createOrUpdateConfiguration() {
        val gson = Gson()

        if (!mcpConfigurationPath.exists()) {
            File(mcpConfigurationPath.toString()).mkdirs()
        }

        fun <S : McpServer, C : McpConfig> createOrUpdate(
            makeServer: () -> S,
            parseConfig: (String) -> C?,
            extractServers: (C) -> Map<String, S>,
            buildConfig: (Map<String, S>) -> Any
        ) {
            val server = makeServer()
            val file = File(mcpConfigurationFilePath.toString())

            if (mcpConfigurationFilePath.exists()) {
                val json = file.readText()
                val existing = try {
                    parseConfig(json)
                } catch (_: Exception) {
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
        }

        when (this) {
            JUNIE -> createOrUpdate(
                makeServer = {
                    McpServerJunie(
                        command = "npx",
                        args = listOf("-y", "@codacy/codacy-mcp"),
                        env = mapOf(
                            "CODACY_ACCOUNT_TOKEN" to "PUT TOKEN HERE", // TODO read from config
                        )
                    )
                },
                parseConfig = { json -> gson.fromJson(json, McpConfigJunie::class.java) },
                extractServers = { cfg -> cfg.mcpServers },
                buildConfig = { servers -> McpConfigJunie(mcpServers = servers) }
            )

            GITHUB_COPILOT -> createOrUpdate(
                makeServer = {
                    McpServerGithubCopilot(
                        type = "stdio",
                        command = "npx",
                        args = listOf("-y", "@codacy/codacy-mcp"),
                        env = mapOf(
                            "CODACY_ACCOUNT_TOKEN" to "PUT TOKEN HERE", // TODO read from config
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
        if(!Config.instance.state.generateGuidelines){
            //TODO notification that it wont be ran
            return
        }

        val basePath = project.basePath ?: throw RuntimeException("Project base path is null")

        val newRules = McpRulesTemplate.newRulesTemplate(
            project = project,
            repositoryParams = params,
            excludedScopes = listOf(RuleScope.GUARDRAILS)
        )

        val rulesPath = guidelinesFilePath(project)
        val dirPath = rulesPath.parent

        if (!dirPath.exists()) {
            dirPath.toFile().mkdirs()
        }


        if (!rulesPath.exists()) {
            rulesPath.writeText(
                McpRulesTemplate.convertRulesToMarkdown(newRules)
            )
            McpRulesTemplate.addRulesToGitignore(basePath, rulesPath)
        } else {
            try {
                val existingContent = rulesPath.readText()
                rulesPath.writeText(
                    McpRulesTemplate.convertRulesToMarkdown(newRules, existingContent)
                )
            } catch (e: Exception) {
                //TODO in vscode there might be parsing error
            }
        }
    }

    fun isPluginEnabled(): Boolean {
        val id = PluginId.getId(pluginId)
        val descriptor = PluginManagerCore.getPlugin(id) ?: return false

        return descriptor.isEnabled && PluginManagerCore.isPluginInstalled(id)
    }

    fun isPluginInstalled(): Boolean = PluginManagerCore
        .getPlugin(PluginId.getId(pluginId)) != null

    //TODO this won't work, we have to check for a specific object
    fun isMcpInstalled(project: Project): Boolean =
        mcpConfigurationFilePath.exists()

    //TODO maybe check if files is not empty
    fun isGuidelinesInstalled(project: Project): Boolean =
        guidelinesFilePath(project).exists()


    fun isGuideInstalled(): Boolean = false //TODO

    object JUNIE : AiAgent() {
        override val pluginId: String = "org.jetbrains.junie"
        override var state: State = State.NOT_INSTALLED

        private val homePath = System.getProperty("user.home")

        override val mcpConfigurationPath: Path = Paths.get(homePath, ".junie", "mcp")
        override val mcpConfigurationFilePath: Path = Paths.get(mcpConfigurationPath.toString(), "mcp.json")


        override fun guidelinesFilePath(project: Project): Path {
            val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")

            return Paths.get(basePath, ".junie", "guidelines.md")
        }

        override fun toString() = "Junie Agent"
    }

    object GITHUB_COPILOT : AiAgent() {
        override val pluginId: String = "com.github.copilot"
        override var state: State = State.NOT_INSTALLED

        private val homePath = System.getProperty("user.home")

        override val mcpConfigurationPath: Path = Paths.get(homePath, ".config", "github-copilot", "intellij")
        override val mcpConfigurationFilePath: Path = Paths.get(mcpConfigurationPath.toString(), "mcp.json")

        override fun guidelinesFilePath(project: Project): Path {
            val basePath = project.basePath ?: throw IllegalStateException("Project base path is null")

            return Paths.get(basePath, ".github", "copilot-instructions.md")
        }

        override fun toString() = "GitHub Copilot Agent"
    }
}
