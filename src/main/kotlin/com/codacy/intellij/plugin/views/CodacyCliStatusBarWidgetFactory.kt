package com.codacy.intellij.plugin.views

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class CodacyCliStatusBarWidgetFactory: StatusBarWidgetFactory {
    override fun getId(): String = "CodacyCliStatusBarWidget"

    override fun getDisplayName(): String = "Codacy CLI Status"

    override fun isAvailable(p0: Project): Boolean = true //TODO

    override fun createWidget(project: Project): StatusBarWidget =
        CodacyCliStatusBarWidget(project)

    override fun disposeWidget(p0: StatusBarWidget) {
        Disposer.dispose(p0)
    }

    override fun canBeEnabledOn(p0: StatusBar): Boolean = true //TODO


}
