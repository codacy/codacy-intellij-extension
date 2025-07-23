package com.codacy.intellij.plugin.views

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class CodacyCliStatusBarWidgetFactory: StatusBarWidgetFactory {
    override fun getId(): String = "com.codacy.intellij.plugin.views.CodacyCliStatusBarWidget"

    override fun getDisplayName(): String = "Codacy CLI Status"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget =
        CodacyCliStatusBarWidget(project)

    override fun disposeWidget(statusBarWidget: StatusBarWidget) {
        Disposer.dispose(statusBarWidget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

}
