package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JPanel

class CodacyCliToolWindowFactory : ToolWindowFactory {

    val panel = JPanel(GridLayout(2, 2))
    val downloadCliButton = JButton("Download CLI")
    val initCliButton = JButton("Initialize CLI")

    @OptIn(DelicateCoroutinesApi::class)
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        StartupManager.getInstance(project).runWhenProjectIsInitialized {
            CodacyCli.getService(
                project,
            ).registerToolWindowFactory(this)

            panel.add(downloadCliButton)
            panel.add(initCliButton)

            downloadCliButton.addActionListener {
                GlobalScope.launch(Dispatchers.IO) {
                    CodacyCli.getService(
                        project,
                    ).prepareCli(false)
                }
            }

            initCliButton.addActionListener {
                GlobalScope.launch(Dispatchers.IO) {
                    CodacyCli.getService(
                        project,
                    ).prepareCli(true)
                }
            }

            val contentFactory = ContentFactory.getInstance()
            val content = contentFactory.createContent(panel, "", false)
            toolWindow.contentManager.addContent(content)
        }
    }
}
