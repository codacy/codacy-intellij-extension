package com.codacy.intellij.plugin.services.common

import com.intellij.openapi.options.SearchableConfigurable
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

class ConfigConfigurable : SearchableConfigurable {

    private var form: ConfigWindowForm? = null

    override fun createComponent(): JComponent? {
        form = ConfigWindowForm()
        return form?.component
    }

    override fun isModified(): Boolean {
        val state = Config.instance.state
        return form?.getCliVersion() != state.cliVersion
    }

    override fun apply() {
        val state = Config.instance.state

        //TODO remove semVer check
//        if (form?.isSemVerValid(form?.getCliVersion() ?: "") ?: false) {
            state.cliVersion = form?.getCliVersion().toString()// todo silly because its nullable
//        }
    }


    override fun reset() {
        val state = Config.instance.state
        form?.setCliVersion(state.cliVersion)
    }

    override fun getDisplayName(): String = "Codacy Plugin Settings"

    override fun getId(): @NonNls String {
        return "codacyPluginSettings"
    }
}
