package com.codacy.intellij.plugin.services.cli

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.AnimatedIcon
import com.intellij.util.Consumer
import java.awt.event.MouseEvent
import javax.swing.Icon

class CodacyCliStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {


    interface State {

        val icon: Icon

        data object INSTALLED : State {
            override fun toString() = "Installed"
            override val icon: Icon = AllIcons.General.ErrorDialog
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
            override val icon: Icon = AllIcons.General.BalloonError
        }

        //This would only be an initial state, not a real step
        data object INIT : State {
            override fun toString() = "Init"
            override val icon: Icon = AllIcons.General.BalloonError //TODO should not be needed
        }
    }

    private var state: State = State.INIT
    var statusBar: StatusBar? = null

    //TODO rename
    override fun ID(): String = "MyPluginStatusWidget"

    override fun install(statusBar: StatusBar) {
        CodacyCli.getService("", "", "", project)
            .registerWidget(this)

        this.statusBar = statusBar
    }

    override fun dispose() {}

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon = state.icon

    override fun getTooltipText(): String {
        return when (state) {
            is State.INSTALLED -> "Codacy CLI is installed and ready to use"
            is State.INSTALLING -> "Codacy CLI is being installed, please wait..."
            is State.ANALYZING -> "Codacy CLI is analyzing your code, please wait..."
            is State.ERROR -> "An error occurred with Codacy CLI: $state"
            is State.INIT -> "Codacy CLI is initializing, please wait..."
            else -> "Something went wrong"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { e ->
        val test = when(state) {
            is State.INSTALLED -> State.INSTALLING
            is State.INSTALLING -> State.ANALYZING
            is State.ANALYZING -> State.ERROR
            is State.ERROR -> State.INIT
            is State.INIT -> State.INSTALLED
            else -> State.INIT
        }
        updateStatus(test)
        JBPopupFactory.getInstance()
            .createMessage("Current step: $state")
            .showInCenterOf(e.component)
    }


    fun updateStatus(state: State) {
        this.state = state
        statusBar?.updateWidget(ID())
    }
}
