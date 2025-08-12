package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.listeners.ServiceState
import com.codacy.intellij.plugin.listeners.WidgetStateListener
import com.codacy.intellij.plugin.services.agent.AiAgent
import com.codacy.intellij.plugin.services.agent.AiAgentService
import com.codacy.intellij.plugin.services.cli.CodacyCliService
import com.codacy.intellij.plugin.services.cli.CodacyCliService.CodacyCliState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
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

    var statusBar: StatusBar? = null

    private var cliService: CodacyCliService? = null
    private var aiAgentService: AiAgentService? = null

    override fun ID(): String = "com.codacy.intellij.plugin.views.CodacyCliStatusBarWidget"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    //Ensures widget will be fully initialized after CodacyCLIService
    // and AIAgentService startup.
    // Registers and event to update its state
    init {
        project.messageBus.connect().subscribe(WidgetStateListener.CLI_TOPIC, object : WidgetStateListener {
            override fun stateChanged(newState: ServiceState) {
                if (newState == ServiceState.RUNNING) {
                    val _cliService = CodacyCliService.getService(project)
                    cliService = _cliService

                    _cliService.addStateListener {
                        statusBar?.updateWidget(ID())
                    }

                    statusBar?.updateWidget(ID())
                }
            }
        })
        project.messageBus.connect().subscribe(WidgetStateListener.AI_AGENT_TOPIC, object : WidgetStateListener {
            override fun stateChanged(newState: ServiceState) {
                if (newState == ServiceState.RUNNING) {
                    aiAgentService = AiAgentService.getService(project)
                }
            }
        })
    }


    override fun dispose() {}

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getIcon(): Icon = cliService?.codacyCliState?.icon ?: CodacyCliState.NOT_INSTALLED.icon

    override fun getTooltipText(): String {
        return when (cliService?.codacyCliState) {
            is CodacyCliState.INSTALLED -> "Codacy CLI is installed, waiting to be initialized"
            is CodacyCliState.INSTALLING -> "Codacy CLI is being installed, please wait..."
            is CodacyCliState.INITIALIZED -> "Codacy CLI is initialized and ready to use"
            is CodacyCliState.ANALYZING -> "Codacy CLI is analyzing your code, please wait..."
            is CodacyCliState.ERROR -> "An error occurred with Codacy CLI: ${cliService?.codacyCliState}"
            is CodacyCliState.NOT_INSTALLED -> "Codacy CLI is not installed, please install it"
            else -> "Something went wrong"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent?> {
        return Consumer { event: MouseEvent? ->
            if (event == null || event.component == null) return@Consumer

            val popup = javax.swing.JPopupMenu()

            val installBtn = installAndInitializeCliButton()
            if (installBtn != null) {
                popup.add(installBtn)
            }

            if (aiAgentService?.mcpAiAgentState == AiAgentService.AiAgentState.NOT_INSTALLED) {
                popup.add(installMcpButton())
            }

            if (aiAgentService?.guidelinesAiAgentState == AiAgentService.AiAgentState.NOT_INSTALLED) {
                popup.add(installGuidelinesButton())
            }

            popup.show(event.component, event.x, event.y)
        }
    }

    private fun installGuidelinesButton(): JMenuItem {
        val installGuidelinesBtn = JMenuItem("Install AiAgent Guidelines")

        installGuidelinesBtn.addActionListener {
            aiAgentService?.installGuidelines(project,null)
        }

        return installGuidelinesBtn
    }

    private fun installMcpButton(): JMenuItem {
        val mcpInstalledBtn = JMenuItem("Install AiAgent MCP")
        mcpInstalledBtn.addActionListener {
            aiAgentService?.createOrUpdateMcpConfiguration()
        }
        return mcpInstalledBtn
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun installAndInitializeCliButton(): JMenuItem? {
        val message = when (cliService?.codacyCliState) {
            is CodacyCliState.ERROR -> "Install and Initialize CLI Again"
            is CodacyCliState.NOT_INSTALLED -> "Install and Initialize CLI"
            is CodacyCliState.INSTALLED -> "Initialize CLI"
            else -> null
        }

        val installCliBtn = if (message != null)
            JMenuItem("Install CLI")
        else null


        installCliBtn?.addActionListener {
            val message = when (cliService?.codacyCliState) {
                is CodacyCliState.ERROR, CodacyCliState.NOT_INSTALLED -> Messages.showYesNoDialog(
                    project,
                    "For CLI to work, you need to install it",
                    "CLI Not Installed",
                    Messages.getYesButton(),
                    Messages.getNoButton(),
                    Messages.getQuestionIcon()
                )

                is CodacyCliState.INSTALLED -> Messages.showYesNoDialog(
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
                        cliService?.prepareCli(true)
                    }
                }
            }
        }

        return installCliBtn
    }
}
