package com.codacy.intellij.plugin.services.common

import com.codacy.intellij.plugin.services.agent.AiAgentName
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_RELEASES_LINK
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SearchableConfigurable
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

class ConfigConfigurable : SearchableConfigurable {

    private var form: ConfigWindowForm? = null
    val config = Config.instance

    override fun createComponent(): JComponent? {
        form = ConfigWindowForm()
        fetchAllAvailableCliVersions()
        populateAiAgentsDropdown()
        // Initialize checkboxes with current state
        form?.setGenerateGuidelines(config.state.allowGenerateGuidelines)
        form?.setAnalyzeGeneratedCode(config.state.addAnalysisGuidelines)
        return form?.component
    }


    fun fetchAllAvailableCliVersions() {
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().invokeLater {
                val state = config.state
                val tagNames = fetchAvailableCliVersions()
                state.availableCliVersions = tagNames
                form?.setAvailableCliVersionsDropdown(tagNames, state.selectedCliVersion)
            }
        }
    }

    private fun populateAiAgentsDropdown() {
        val state = config.state
        val agents = listOf(
            AiAgentName.JUNIE,
            AiAgentName.GITHUB_COPILOT
        )
        form?.setAvailableAiAgentsDropdown(agents, state.selectedAiAgent)
    }

    override fun isModified(): Boolean {
        val state = Config.instance.state
        val versionChanged = form?.getSelectedAvailableCliVersion() != state.selectedCliVersion
        val selectedAgentChanged = form?.getSelectedAiAgent() != state.selectedAiAgent
        val generateGuidelinesChanged = (form?.getGenerateGuidelines() ?: false) != state.allowGenerateGuidelines
        val analyzeGeneratedCodeChanged = (form?.getAnalyzeGeneratedCode() ?: false) != state.addAnalysisGuidelines
        return versionChanged || selectedAgentChanged || generateGuidelinesChanged || analyzeGeneratedCodeChanged
    }

    override fun apply() {
        val state = Config.instance.state
        state.selectedCliVersion = form?.getSelectedAvailableCliVersion() ?: ""
        state.selectedAiAgent = form?.getSelectedAiAgent() ?: AiAgentName.JUNIE
        state.allowGenerateGuidelines = form?.getGenerateGuidelines() ?: false
        state.addAnalysisGuidelines = form?.getAnalyzeGeneratedCode() ?: false
        // Ensure settings are persisted immediately
        ApplicationManager.getApplication().saveSettings()
    }

    override fun reset() {
        val state = Config.instance.state
        form?.setAvailableCliVersionsDropdown(state.availableCliVersions, state.selectedCliVersion)
        populateAiAgentsDropdown()
        form?.setGenerateGuidelines(state.allowGenerateGuidelines)
        form?.setAnalyzeGeneratedCode(state.addAnalysisGuidelines)
    }

    override fun getDisplayName(): String = "Codacy Plugin Settings"

    override fun getId(): @NonNls String {
        return "codacyPluginSettings"
    }

    private fun fetchAvailableCliVersions(): List<String> {
        val process = ProcessBuilder("curl", CODACY_CLI_RELEASES_LINK)
            .redirectErrorStream(false)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Curl failed with exit code $exitCode")
        }

        return extractTagNames(output)
    }

    private fun extractTagNames(jsonString: String): List<String> {
        val jsonElement = JsonParser.parseString(jsonString)

        if (!jsonElement.isJsonArray) {
            throw IllegalArgumentException("Expected JSON array")
        }

        val jsonArray = jsonElement.asJsonArray
        val tagNames = mutableListOf<String>()

        for (element in jsonArray) {
            val obj = element.asJsonObject
            if (obj.has("tag_name")) {
                tagNames.add(obj["tag_name"].asString)
            }
        }

        return tagNames
    }


}
