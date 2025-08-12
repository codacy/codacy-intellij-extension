package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.listeners.ServiceState
import com.codacy.intellij.plugin.listeners.WidgetStateListener
import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.codacy.intellij.plugin.services.cli.CodacyCli.CliState
import com.codacy.intellij.plugin.services.mcp.AiAgentService
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

class CodacyCliStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.IconPresentation {

    //TODO
    private data class AiStatus(val mcpInstalled: Boolean, val guidelinesInstalled: Boolean)

    var statusBar: StatusBar? = null
    private var cliService: CodacyCli? = null

    override fun ID(): String = "com.codacy.intellij.plugin.views.CodacyCliStatusBarWidget"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    init {
        project.messageBus.connect().subscribe(WidgetStateListener.TOPIC, object : WidgetStateListener {
            override fun stateChanged(newState: ServiceState) {
                if (newState == ServiceState.RUNNING) {
                    val _cliService = CodacyCli.getService(project)
                    cliService = _cliService
                    init(_cliService)

                    statusBar?.updateWidget(ID())
                }
            }
        })
    }

    //TODO
    fun init(cliService: CodacyCli) {
        cliService.addStateListener { newState ->
//            _icon = newState.icon
            this.cliState = newState
            statusBar?.updateWidget(ID())
        }
    }

//    var _icon: Icon = AnimatedIcon.Default()

    var cliState: CodacyCli.CliState? = null

    override fun dispose() {}

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon = cliService?.cliState?.icon ?: CliState.NOT_INSTALLED.icon

    override fun getTooltipText(): String {
        return when (cliState) {
            is CodacyCli.CliState.INSTALLED -> "Codacy CLI is installed, waiting to be initialized"
            is CodacyCli.CliState.INSTALLING -> "Codacy CLI is being installed, please wait..."
            is CodacyCli.CliState.INITIALIZED -> "Codacy CLI is initialized and ready to use"
            is CodacyCli.CliState.ANALYZING -> "Codacy CLI is analyzing your code, please wait..."
            is CodacyCli.CliState.ERROR -> "An error occurred with Codacy CLI: $cliState"
            is CodacyCli.CliState.NOT_INSTALLED -> "Codacy CLI is not installed, please install it"
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
                is CodacyCli.CliState.ERROR, CodacyCli.CliState.NOT_INSTALLED -> Messages.showYesNoDialog(
                    project,
                    "For CLI to work, you need to install it",
                    "CLI Not Installed",
                    Messages.getYesButton(),
                    Messages.getNoButton(),
                    Messages.getQuestionIcon()
                )

                is CodacyCli.CliState.INSTALLED -> Messages.showYesNoDialog(
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

    fun updateStatus(cliState: CodacyCli.CliState) {
//        this.cliState = cliState
        statusBar?.updateWidget(ID())
    }
}
