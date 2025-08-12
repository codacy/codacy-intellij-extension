package com.codacy.intellij.plugin.services.common

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class ConfigWindowForm {

    private val panel: JPanel = JPanel()
    private val availableCliVersionsDropdownMenu = ComboBox<String>()
    private val generateGuidelinesCheckbox = JCheckBox("Generate Guidelines")

    val component: JComponent get() = panel

    init {
        panel.add(
            panel {
                row("Codacy CLI Version:") {
                    cell(availableCliVersionsDropdownMenu)
                }
                row {
                    cell(generateGuidelinesCheckbox)
                }
            }
        )
    }

    fun setAvailableCliVersionsDropdown(items: List<String>, selected: String?) {
        availableCliVersionsDropdownMenu.removeAllItems()
        items.forEach { availableCliVersionsDropdownMenu.addItem(it) }
        selectAvailableCliVersion(selected)
    }

    fun selectAvailableCliVersion(item: String?) {
        if (item != null) {
            availableCliVersionsDropdownMenu.selectedItem = item
        } else {
            availableCliVersionsDropdownMenu.selectedIndex = -1
        }
    }

    fun getSelectedAvailableCliVersion(): String? {
        return availableCliVersionsDropdownMenu.selectedItem as? String
    }

    fun setGenerateGuidelines(value: Boolean) {
        generateGuidelinesCheckbox.isSelected = value
    }

    fun getGenerateGuidelines(): Boolean {
        return generateGuidelinesCheckbox.isSelected
    }
}
