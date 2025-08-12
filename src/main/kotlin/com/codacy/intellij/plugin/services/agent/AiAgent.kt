package com.codacy.intellij.plugin.services.agent

import com.codacy.intellij.plugin.services.agent.model.McpConfigGithubCopilot
import com.codacy.intellij.plugin.services.agent.model.McpConfigJunie
import com.codacy.intellij.plugin.services.agent.model.McpServerGithubCopilot
import com.codacy.intellij.plugin.services.agent.model.McpServerJunie
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.exists
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

sealed class AiAgent() {

    abstract val pluginId: String

    abstract val mcpConfigurationPath: Path
    abstract val mcpConfigurationFilePath: Path
    abstract val guidelinesFilePath: Path

    abstract val projectPath: Path
    abstract val homePath: Path

    fun isPluginEnabled(): Boolean {
        val id = PluginId.getId(pluginId)
        val descriptor = PluginManagerCore.getPlugin(id) ?: return false

        return descriptor.isEnabled && PluginManagerCore.isPluginInstalled(id)
    }

    fun isPluginInstalled(): Boolean = PluginManagerCore
        .getPlugin(PluginId.getId(pluginId)) != null

    fun isMcpInstalled(token: String?): Boolean {
        if (token.isNullOrBlank()) return false

        val path = mcpConfigurationFilePath
        if (!path.exists() || !path.isRegularFile()) return false

        val content = runCatching { path.readText() }.getOrElse { return false }
        val root: JsonElement = runCatching { JsonParser.parseString(content) }.getOrElse { return false }
        if (!root.isJsonObject) return false

        val obj = root.asJsonObject

        val gson = Gson()

        fun buildExpectedServer(): JsonObject {
            return when (this@AiAgent) {
                is JUNIE -> gson.toJsonTree(
                    McpServerJunie(
                        command = "npx",
                        args = listOf("-y", "@codacy/codacy-mcp"),
                        env = mapOf("CODACY_ACCOUNT_TOKEN" to token)
                    )
                ).asJsonObject
                is GITHUB_COPILOT -> gson.toJsonTree(
                    McpServerGithubCopilot(
                        type = "stdio",
                        command = "npx",
                        args = listOf("-y", "@codacy/codacy-mcp"),
                        env = mapOf("CODACY_ACCOUNT_TOKEN" to token)
                    )
                ).asJsonObject
            }
        }

        fun serversContainerName(): String {
            val cfgJson = when (this@AiAgent) {
                is JUNIE -> gson.toJsonTree(
                    McpConfigJunie(emptyMap())
                ).asJsonObject
                is GITHUB_COPILOT -> gson.toJsonTree(
                    McpConfigGithubCopilot(emptyMap())
                ).asJsonObject
            }
            return cfgJson.entrySet().firstOrNull()?.key ?: return ""
        }

        val expected = buildExpectedServer()
        val serversObj = obj.getAsJsonObject(serversContainerName()) ?: return false

        return serversObj.entrySet().any { (_, value) -> value.isJsonObject && value.asJsonObject == expected }
    }

    fun isGuidelinesInstalled(): Boolean = guidelinesFilePath.exists() &&
                Files.isRegularFile(guidelinesFilePath) &&
                runCatching { Files.size(guidelinesFilePath) }.getOrDefault(0L) > 0L


    class JUNIE(override val projectPath: Path, override val homePath: Path) : AiAgent() {
        override val pluginId: String = "org.jetbrains.junie"

        override val mcpConfigurationPath: Path =
            Paths.get(homePath.toString(), ".junie", "mcp")

        override val mcpConfigurationFilePath: Path =
            Paths.get(mcpConfigurationPath.toString(), "mcp.json")

        override val guidelinesFilePath: Path =
            Paths.get(projectPath.toString(), ".junie", "guidelines.md")

        override fun toString() = "Junie Agent"
    }

    class GITHUB_COPILOT(override val projectPath: Path, override val homePath: Path) : AiAgent() {
        override val pluginId: String = "com.github.copilot"

        override val mcpConfigurationPath: Path =
            Paths.get(homePath.toString(), ".config", "github-copilot", "intellij")

        override val mcpConfigurationFilePath: Path =
            Paths.get(mcpConfigurationPath.toString(), "mcp.json")

        override val guidelinesFilePath: Path =
            Paths.get(projectPath.toString(), ".github", "copilot-instructions.md")

        override fun toString() = "GitHub Copilot Agent"
    }
}
