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
        return true
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return CodacyStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }
}
