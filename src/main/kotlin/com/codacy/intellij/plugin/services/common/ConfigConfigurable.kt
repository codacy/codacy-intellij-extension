package com.codacy.intellij.plugin.services.common

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
        // Initialize checkbox with current state
        form?.setGenerateGuidelines(config.state.generateGuidelines)
        return form?.component
    }


    fun fetchAllAvailableCliVersions() {
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().invokeLater {
                val state = config.state
                if (state.availableCliVersions.isEmpty()) {
                    val tagNames = fetchAvailableCliVersions()
                    state.availableCliVersions = tagNames
                    form?.setAvailableCliVersionsDropdown(tagNames, state.selectedCliVersion)
                } else {
                    form?.setAvailableCliVersionsDropdown(state.availableCliVersions, state.selectedCliVersion)
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val state = Config.instance.state
        val versionChanged = form?.getSelectedAvailableCliVersion() != state.selectedCliVersion
        val generateGuidelinesChanged = (form?.getGenerateGuidelines() ?: false) != state.generateGuidelines
        return versionChanged || generateGuidelinesChanged
    }

    override fun apply() {
        val state = Config.instance.state
        state.selectedCliVersion = form?.getSelectedAvailableCliVersion() ?: ""
        state.generateGuidelines = form?.getGenerateGuidelines() ?: false
    }

    override fun reset() {
        val state = Config.instance.state
        form?.setAvailableCliVersionsDropdown(state.availableCliVersions, state.selectedCliVersion)
        form?.setGenerateGuidelines(state.generateGuidelines)
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
