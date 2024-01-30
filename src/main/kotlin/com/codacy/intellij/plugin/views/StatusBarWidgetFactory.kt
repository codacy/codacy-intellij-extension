package com.codacy.intellij.plugin.views

import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.project.Project

class CodacyStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String {
        return "com.codacy.intellij.plugin.CodacyStatusBarWidget"
    }

    override fun getDisplayName(): String {
        return "Codacy Pull Request Status"
    }

    override fun isAvailable(project: Project): Boolean {
        // Implement logic to determine if the widget should be available for the given project
        return true
    }

    override fun createWidget(project: Project): StatusBarWidget {
        // Create and return an instance of your CodacyStatusBarWidget
        return CodacyStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        // Dispose of your widget if needed
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        // Implement any logic if your widget can be enabled on the given status bar
        return true
    }
}
