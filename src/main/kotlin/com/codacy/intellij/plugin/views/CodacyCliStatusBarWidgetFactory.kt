package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class CodacyCliStatusBarWidgetFactory: StatusBarWidgetFactory {
    override fun getId(): String = "com.codacy.intellij.plugin.views.CodacyCliStatusBarWidget"

    override fun getDisplayName(): String = "Codacy CLI Status"

    override fun isAvailable(project: Project): Boolean = true

    companion object {
        //TODO: This might be not the best way to handle it,
        // but CLI Service needs access to the widget,
        // and the widget will be instantiated before
        // StartupListener for some reason.
//        var widget: CodacyCliStatusBarWidget? = null
    }

    override fun createWidget(project: Project): StatusBarWidget {
        //TODO wont this create problems
        val widget = CodacyCliStatusBarWidget(project)
//        Companion.widget = widget
        return widget
    }

    override fun disposeWidget(statusBarWidget: StatusBarWidget) {
        Disposer.dispose(statusBarWidget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

}
