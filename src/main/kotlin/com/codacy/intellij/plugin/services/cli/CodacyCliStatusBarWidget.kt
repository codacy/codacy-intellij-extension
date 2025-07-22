package com.codacy.intellij.plugin.services.cli

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.AnimatedIcon
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JLabel

class CodacyCliStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {

    private var isLoading = false
    private var currentStep: String = "Idle"
    private var statusBar: StatusBar? = null

    override fun ID(): String = "MyPluginStatusWidget"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {}

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon =
        if (isLoading) AnimatedIcon.Default.INSTANCE else JLabel("✔").icon

    override fun getTooltipText(): String =
        if (isLoading) "Working: $currentStep" else "Plugin installed and idle"

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { e ->
        JBPopupFactory.getInstance()
            .createMessage("Current step: $currentStep")
            .showInCenterOf(e.component)
    }

    fun updateStatus(loading: Boolean, step: String = "Idle") {
        isLoading = loading
        currentStep = step
        statusBar?.updateWidget(ID())
    }
}
