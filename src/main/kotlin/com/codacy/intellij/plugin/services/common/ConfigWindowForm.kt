package com.codacy.intellij.plugin.services.common

import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ConfigWindowForm {

    private val panel: JPanel = JPanel()
    private val cliVersionField = JTextField(30)
    private val cliVersionWarningLabel = JLabel()

    val component: JComponent get() = panel

    init {
        cliVersionWarningLabel.foreground = JBColor.RED
        cliVersionWarningLabel.isVisible = true
        cliVersionWarningLabel.text = "The value is not a valid semantic version. Value will not be saved."

        cliVersionField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                validateSemVer()
            }

            override fun removeUpdate(e: DocumentEvent?) {
                validateSemVer()
            }

            override fun changedUpdate(e: DocumentEvent?) {
                validateSemVer()
            }
        })


        panel.add(
            panel {
                row("Codacy CLI Version:") {
                    cell(cliVersionField)
                    cell(cliVersionWarningLabel)
                }

            }
        )
    }

    fun getCliVersion(): String {
        return cliVersionField.text
    }

    fun setCliVersion(version: String) {
        cliVersionField.text = version
    }

    fun isSemVerValid(input: String): Boolean {
        return Regex("\\d+\\.\\d+\\.\\d+", RegexOption.IGNORE_CASE)
            .matches(input)
    }

    private fun validateSemVer() {
        cliVersionWarningLabel.isVisible = !isSemVerValid(cliVersionField.text)
    }

}
