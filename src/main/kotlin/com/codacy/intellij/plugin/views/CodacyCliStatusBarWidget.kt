package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.codacy.intellij.plugin.services.common.IconUtils
import com.codacy.intellij.plugin.services.mcp.AiAgentService
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
import javax.swing.JMenuItem

class CodacyCliStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {

    //TODO
    private data class AiStatus(val mcpInstalled: Boolean, val guidelinesInstalled: Boolean)

    sealed interface CliState {
        val icon: Icon

        data object INSTALLED : CliState {
            override fun toString() = "Installed"
            override val icon: Icon = AllIcons.General.Information
        }

        data object INITIALIZED : CliState {
            override fun toString() = "Initialized"
            override val icon: Icon = IconUtils.CodacyIcon
        }

        data object INSTALLING : CliState {
            override fun toString() = "Installing"
            override val icon: Icon = AnimatedIcon.Default.INSTANCE
        }

        data object ANALYZING : CliState {
            override fun toString() = "Analyzing"
            override val icon: Icon = AnimatedIcon.Default.INSTANCE
        }

        data object ERROR : CliState {
            override fun toString() = "Error"
            override val icon: Icon = AllIcons.General.ErrorDialog
        }

        data object NOT_INSTALLED : CliState {
            override fun toString() = "Not Installed"
            override val icon: Icon = AllIcons.General.Gear
        }
    }

    var statusBar: StatusBar? = null

    private var cliState: CliState = CliState.NOT_INSTALLED

    override fun ID(): String = "com.codacy.intellij.plugin.views.CodacyCliStatusBarWidget"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {}

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon = cliState.icon

    override fun getTooltipText(): String {
        return when (cliState) {
            is CliState.INSTALLED -> "Codacy CLI is installed, waiting to be initialized"
            is CliState.INSTALLING -> "Codacy CLI is being installed, please wait..."
            is CliState.INITIALIZED -> "Codacy CLI is initialized and ready to use"
            is CliState.ANALYZING -> "Codacy CLI is analyzing your code, please wait..."
            is CliState.ERROR -> "An error occurred with Codacy CLI: $cliState"
            is CliState.NOT_INSTALLED -> "Codacy CLI is not installed, please install it"
            else -> "Something went wrong"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent?> {
        return Consumer { event: MouseEvent? ->
            if (event == null || event.component == null) return@Consumer

            val aiAgentStatus = getAiAgentStatus()
            val cliStatus = getCliStatus()

            val popup = javax.swing.JPopupMenu()

            if (!cliStatus) {
               popup.add(installCliButton())
            }

            if (!aiAgentStatus.mcpInstalled) {
                popup.add(installMcpButton())
            }

            if (!aiAgentStatus.guidelinesInstalled) {
                popup.add(installGuidelinesButton())
            }

            popup.show(event.component, event.x, event.y)
        }
    }

    private fun installGuidelinesButton(): JMenuItem {
        val installGuidelinesBtn = JMenuItem("Install AiAgent Guidelines")

        installGuidelinesBtn.addActionListener {
            AiAgentService.getService(project)
                .installGuidelines(null)//TODO
        }

        return installGuidelinesBtn
    }

    private fun installMcpButton(): JMenuItem {
        val mcpInstalledBtn = JMenuItem("Install AiAgent MCP")
        mcpInstalledBtn.addActionListener {
            AiAgentService.getService(project)
                .createOrUpdateMcpConfiguration()
        }
        return mcpInstalledBtn
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun installCliButton(): JMenuItem {
        val installCliBtn = JMenuItem("Install CLI")
        installCliBtn.addActionListener {
            val message = when (cliState) {
                is CliState.ERROR, CliState.NOT_INSTALLED -> Messages.showYesNoDialog(
                    project,
                    "For CLI to work, you need to install it",
                    "CLI Not Installed",
                    Messages.getYesButton(),
                    Messages.getNoButton(),
                    Messages.getQuestionIcon()
                )

                is CliState.INSTALLED -> Messages.showYesNoDialog(
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
                        CodacyCli.getService(project).prepareCli(true)
                    }
                }
            }
        }

        return installCliBtn
    }

    private fun getAiAgentStatus(): AiStatus {
        val aiAgentService = project.getService(AiAgentService::class.java)
        val isMcpInstalled = aiAgentService.aiAgent.isMcpInstalled(project)
        val isGuidelinesInstalled = aiAgentService.aiAgent.isGuidelinesInstalled(project)

        return AiStatus(isMcpInstalled, isGuidelinesInstalled)
    }

    //TODO return type
    private fun getCliStatus(): Boolean {
        val cliService = CodacyCli.getService(project)
        return cliService.isCliShellFilePresent() &&
                cliService.isCodacySettingsPresent() &&
                cliService.isCodacyDirectoryPresent()
    }

    fun updateStatus(cliState: CliState) {
        this.cliState = cliState
        statusBar?.updateWidget(ID())
    }
}
