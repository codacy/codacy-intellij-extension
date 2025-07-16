package com.codacy.intellij.plugin.services.common

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
        return form?.component
    }


    fun fetchAllAvailableCliVersions() {
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().invokeLater {
                val state = config.state
                if (state.availableCliVersions.isEmpty()) {
                    val versions = fetchAvailableCliVersions()
                    val tagNames = extractTagNames(versions)
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
        return form?.getSelectedAvailableCliVersion() != state.selectedCliVersion
    }

    override fun apply() {
        val state = Config.instance.state
        state.selectedCliVersion = form?.getSelectedAvailableCliVersion()!! //TODO forcing here
    }

    override fun reset() {
        val state = Config.instance.state
        form?.setAvailableCliVersionsDropdown(state.availableCliVersions, state.selectedCliVersion)
    }

    override fun getDisplayName(): String = "Codacy Plugin Settings"

    override fun getId(): @NonNls String {
        return "codacyPluginSettings"
    }

    //fetch JSON from https://api.github.com/repos/codacy/codacy-cli-v2/releases
    // extract tag_name from each object and list it as a list of strings
    fun fetchAvailableCliVersions(): String {
        //TODO improve, put the value somewhere, maybe in Config class
        val process = ProcessBuilder("curl", "https://api.github.com/repos/codacy/codacy-cli-v2/releases")
            .redirectErrorStream(false)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Curl failed with exit code $exitCode")
        }

        return output
    }

    fun extractTagNames(jsonString: String): List<String> {
        val jsonElement = JsonParser.parseString(jsonString)

        // Ensure it's a JSON array at the top level
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
