package com.codacy.intellij.plugin.services.common

import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class ConfigWindowForm {
    lateinit var panel: JPanel
    lateinit var mySettingField: JTextField

    val component: JComponent get() = panel

    fun getMySetting(): String = mySettingField.text
    fun setMySetting(value: String) {
        mySettingField.text = value
    }
}
