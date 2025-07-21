package com.codacy.intellij.plugin.services.common

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPanel

class ConfigWindowForm {

    private val panel: JPanel = JPanel()
    private val availableCliVersionsDropdownMenu = ComboBox<String>()

    val component: JComponent get() = panel

    init {
        panel.add(
            panel {
                row("Codacy CLI Version:") {
                    cell(availableCliVersionsDropdownMenu)
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
}
