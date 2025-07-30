package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.codacy.intellij.plugin.services.common.IconUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.AnimatedIcon
import com.intellij.util.Consumer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent
import javax.swing.Icon

class CodacyCliStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {

    interface State {
        val icon: Icon

        data object INSTALLED : State {
            override fun toString() = "Installed"
            override val icon: Icon = AllIcons.General.Information
        }

        data object INITIALIZED : State {
            override fun toString() = "Initialized"
            override val icon: Icon = IconUtils.CodacyIcon
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
            override val icon: Icon = AllIcons.General.ErrorDialog
        }

        data object NOT_INSTALLED : State {
            override fun toString() = "Not Installed"
            override val icon: Icon = AllIcons.General.Gear
        }
    }

    var statusBar: StatusBar? = null

    private var state: State = State.NOT_INSTALLED

    override fun ID(): String = "com.codacy.intellij.plugin.views.CodacyCliStatusBarWidget"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {}

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon = state.icon

    override fun getTooltipText(): String {
        return when (state) {
            is State.INSTALLED -> "Codacy CLI is installed, waiting to be initialized"
            is State.INSTALLING -> "Codacy CLI is being installed, please wait..."
            is State.INITIALIZED -> "Codacy CLI is initialized and ready to use"
            is State.ANALYZING -> "Codacy CLI is analyzing your code, please wait..."
            is State.ERROR -> "An error occurred with Codacy CLI: $state"
            is State.NOT_INSTALLED -> "Codacy CLI is not installed, please install it"
            else -> "Something went wrong"
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun getClickConsumer(): Consumer<MouseEvent?>? {
        return Consumer { event: MouseEvent? ->

            val message = when (state) {
                is State.ERROR, State.NOT_INSTALLED -> Messages.showYesNoDialog(
                    project,
                    "For CLI to work, you need to install it",
                    "CLI Not Installed",
                    Messages.getYesButton(),
                    Messages.getNoButton(),
                    Messages.getQuestionIcon()
                )
                is State.INSTALLED -> Messages.showYesNoDialog(
                    project,
                    "CLI is installed, but not initialized. Would you like to initialize it now?",
                    "Partial CLI Installation",
                    Messages.getYesButton(),
                    Messages.getNoButton(),
                    Messages.getQuestionIcon()
                )
                else -> null
            }

            if (message != null) {
                if (message == Messages.YES) {
                    GlobalScope.launch(Dispatchers.IO) {
                        CodacyCli.Companion.getService(project).prepareCli(true)
                    }
                }
            }
        }
    }


    fun updateStatus(state: State) {
        this.state = state
        statusBar?.updateWidget(ID())
    }
}
