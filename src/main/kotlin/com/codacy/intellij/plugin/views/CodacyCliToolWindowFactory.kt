package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.codacy.intellij.plugin.services.cli.CodacyCliStatusBarWidgetFactory
import com.codacy.intellij.plugin.services.common.GitRemoteParser
import com.codacy.intellij.plugin.services.git.GitProvider
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class CodacyCliToolWindowFactory : ToolWindowFactory {

    val panel = JPanel(GridLayout(2, 2))
    val cliPresentLabel = JLabel("Codacy CLI is not installed.")
    val cliSettingsPresentLabel = JLabel("Codacy CLI is not initialized.")
    val downloadCliButton = JButton("Download CLI")
    val initCliButton = JButton("Initialize CLI")

    val testButton = JButton("Test Status Bar Widget")

    @OptIn(DelicateCoroutinesApi::class)
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        StartupManager.getInstance(project).runWhenProjectIsInitialized {
            val gitProvider = GitProvider.getRepository(project)
            if (gitProvider != null) {
                val remote = gitProvider.remotes.firstOrNull()

                if (remote == null) {
                    notificationGroup.createNotification(
                        "No remote found.",
                        "Please make sure you have a valid Git remote in the project.",
                        NotificationType.ERROR
                    ).notify(project)
                    return@runWhenProjectIsInitialized
                }

                val gitInfo = GitRemoteParser.parseGitRemote(remote.firstUrl!!)


                CodacyCli.getService(
                    gitInfo.provider,
                    gitInfo.organization,
                    gitInfo.repository,
                    project,
                ).registerToolWindowFactory(this)

                panel.add(downloadCliButton)
                panel.add(initCliButton)
                panel.add(cliPresentLabel)
                panel.add(cliSettingsPresentLabel)
                panel.add(testButton)

                downloadCliButton.addActionListener {
                    GlobalScope.launch(Dispatchers.IO) {
                        CodacyCli.getService(
                            gitInfo.provider,
                            gitInfo.organization,
                            gitInfo.repository,
                            project,
                        ).prepareCli(false)
                    }
                }

                initCliButton.addActionListener {
                    GlobalScope.launch(Dispatchers.IO) {
                        CodacyCli.getService(
                            gitInfo.provider,
                            gitInfo.organization,
                            gitInfo.repository,
                            project,
                        ).prepareCli(true)
                    }
                }

                testButton.addActionListener {
                    StatusBarWidgetsManager(project)
                        .updateWidget(CodacyCliStatusBarWidgetFactory::class.java)
                }

                val contentFactory = ContentFactory.getInstance()
                val content = contentFactory.createContent(panel, "", false)
                toolWindow.contentManager.addContent(content)

            } else {
                notificationGroup.createNotification(
                    "There was no git repository found.",
                    "Please make sure you have a valid Git repository in the project.",
                    NotificationType.ERROR
                ).notify(project)
            }
        }
    }

    fun updateCliStatus(isShellFilePresent: Boolean, isSettingsPresent: Boolean) {
        if (isShellFilePresent) {
            cliPresentLabel.text = "Codacy CLI is installed."
            cliPresentLabel.icon = AllIcons.General.InspectionsOK
        } else {
            cliPresentLabel.text = "Codacy CLI is not installed."
            cliPresentLabel.icon = AllIcons.General.BalloonError
        }

        if (isSettingsPresent) {
            cliSettingsPresentLabel.text = "Codacy CLI is initialized."
            cliSettingsPresentLabel.icon = AllIcons.General.InspectionsOK
        } else {
            cliSettingsPresentLabel.text = "Codacy CLI is not initialized."
            cliSettingsPresentLabel.icon = AllIcons.General.BalloonError
        }
    }
}
