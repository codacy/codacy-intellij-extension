package com.codacy.intellij.plugin.services.common

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class ConfigWindow : Configurable {
    private var form: ConfigWindowForm? = null

    override fun createComponent(): JComponent? {
        form = ConfigWindowForm()
        return form?.component
    }

    override fun isModified(): Boolean {
        val config = ConfigService.getInstance().state
        return form?.getMySetting() != config.mySetting
    }

    override fun apply() {
        val config = ConfigService.getInstance().state
        config.mySetting = form?.getMySetting() ?: "default"
    }

    override fun reset() {
        val config = ConfigService.getInstance().state
        form?.setMySetting(config.mySetting)
    }

    override fun getDisplayName(): String = "My Plugin Settings"
}
