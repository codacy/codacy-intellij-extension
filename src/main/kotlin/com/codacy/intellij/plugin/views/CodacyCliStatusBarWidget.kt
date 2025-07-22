package com.codacy.intellij.plugin.views

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

    interface State {
        val icon: Icon

        data object INSTALLED : State {
            override fun toString() = "Installed"
            override val icon: Icon = JLabel("✔").icon
        }

        data object INSTALLING : State {
            override fun toString() = "Installing"
            override val icon: Icon = AnimatedIcon.Default.INSTANCE
        }

        data object ANALYZING : State {
            override fun toString() = "Analyzing"
            override val icon: Icon = AnimatedIcon.Default.INSTANCE
        }

        data object ERROR : State {
            override fun toString() = "Error"
            override val icon: Icon = JLabel("✖").icon
        }

        data object INIT : State {
            override fun toString() = "Init"
            override val icon: Icon = JLabel("...").icon
        }
    }

    private var state: State = State.INIT
    private var statusBar: StatusBar? = null

    override fun ID(): String = "MyPluginStatusWidget"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {}

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon {
        return when (state) {
            is State.INSTALLED -> State.INSTALLED.icon
            is State.INSTALLING -> State.INSTALLING.icon
            is State.ANALYZING -> State.ANALYZING.icon
            is State.ERROR -> State.ERROR.icon
            is State.INIT -> State.INIT.icon
            else -> State.INIT.icon
        }
    }


    override fun getTooltipText(): String {
        return when (state) {
            is State.INSTALLED -> "Codacy CLI is installed and ready to use."
            is State.INSTALLING -> "Codacy CLI is currently being installed."
            is State.ANALYZING -> "Codacy CLI is analyzing your code."
            is State.ERROR -> "An error occurred with Codacy CLI."
            is State.INIT -> "Codacy CLI is initializing."
            else -> "Unknown state."
        }
    }

    fun updateState(newState: State) {
        state = newState
        statusBar?.updateWidget(ID())
    }

    //TODO might not be needed
    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { e ->
        JBPopupFactory.getInstance()
            .createMessage("Current step: $state")
            .showInCenterOf(e.component)
    }
}
