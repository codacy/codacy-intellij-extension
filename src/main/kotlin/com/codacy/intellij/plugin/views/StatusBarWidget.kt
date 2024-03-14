package com.codacy.plugin.views

import com.codacy.plugin.services.git.RepositoryManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.*
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.ui.AnimatedIcon
import com.intellij.util.Alarm
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseEvent
import javax.swing.Icon
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D

class RotatedIcon(private val icon: Icon, private val angle: Double) : Icon {
    override fun getIconWidth(): Int = icon.iconHeight  // Swap dimensions for 90 or 270 degrees

    override fun getIconHeight(): Int = icon.iconWidth  // Swap dimensions for 90 or 270 degrees

    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        val g2 = g as Graphics2D

        val centerX = x + icon.iconWidth / 2.0
        val centerY = y + icon.iconHeight / 2.0

        g2.rotate(Math.toRadians(angle), centerX, centerY)
        icon.paintIcon(c, g2, x, y)
        g2.rotate(-Math.toRadians(angle), centerX, centerY)  // Reset rotation for other icons
    }
}

@Suppress("UnstableApiUsage")
class CodacyStatusBarWidget (
    project: Project,
) :
    EditorBasedWidget(project), MultipleTextValuesPresentation, Multiframe {

    override fun ID(): String {
        return "com.codacy.plugin.CodacyStatusBarWidget"
    }

    private val repositoryManager = project.service<RepositoryManager>()

    private var myText: String? = "Codacy"

    private var myTooltip: @NlsContexts.Tooltip String? = null

    private var myIcon: Icon? = null

    private val myUpdateBackgroundAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)

        repositoryManager.onDidUpdatePullRequest {
            ApplicationManager.getApplication().invokeLater {
                updateLater()
            }
        }
        repositoryManager.onDidLoadRepository {
            ApplicationManager.getApplication().invokeLater {
                updateLater()
            }
        }
        repositoryManager.onDidChangeState {
            ApplicationManager.getApplication().invokeLater {
                updateLater()
            }
        }

        updateLater()
    }

    override fun copy(): StatusBarWidget {
        TODO("Not yet implemented")
    }

    override fun getPresentation(): WidgetPresentation {
        return this
    }

    @RequiresEdt
    override fun getSelectedValue(): String? {
        return StringUtil.defaultIfEmpty(myText, "")
    }

    override fun getTooltipText(): String? {
        return myTooltip
    }

    override fun getPopup(): JBPopup? {
        if (isDisposed) return null
        return null
    }

    private fun clearStatus() {
        myText = null
        myTooltip = null
        myIcon = null
    }

    private fun updateLater() {
        UIUtil.invokeLaterIfNeeded {
            if (isDisposed) {
                return@invokeLaterIfNeeded
            }
            myUpdateBackgroundAlarm.cancelAllRequests()
            myUpdateBackgroundAlarm.addRequest({
                if (isDisposed) {
                    clearStatus()
                    return@addRequest
                }
                if (!ProgressIndicatorUtils.runInReadActionWithWriteActionPriority {
                        updateOnBackground()
                    }) {
                    updateLater()
                }
            }, 10)
        }
    }

    @RequiresReadLock
    private fun updateOnBackground() {
        try {
        } catch (e: ProcessCanceledException) {
            return
        } catch (t: Throwable) {
            LOG.error(t)
            clearStatus()
            return
        }

        myTooltip = getToolTip()
        myIcon = getIcon()

        if (myStatusBar != null) myStatusBar.updateWidget(ID())
    }

    private fun getToolTip(): String? {
        val pr = repositoryManager.pullRequest?.prWithAnalysis ?: return null
        return when {
            pr.isAnalysing -> "Analyzing..."
            pr.isUpToStandards == true -> "Up to standards."
            pr.isUpToStandards == false -> "Not up to standards."
            else -> "Not analysed."
        }
    }

    override fun getIcon(): Icon? {
        val pr = repositoryManager.pullRequest?.prWithAnalysis ?: return null
        val loadingIcon = AnimatedIcon(
            250,
            AllIcons.Actions.Refresh,
            RotatedIcon(AllIcons.Actions.Refresh, -90.0)
        )
        return when {
            pr.isAnalysing -> loadingIcon
            pr.isUpToStandards == true -> AllIcons.General.InspectionsOK
            pr.isUpToStandards == false -> AllIcons.General.BalloonError
            else -> AllIcons.General.BalloonInformation
        }
    }
    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer {
            ToolWindowManager.getInstance(project).getToolWindow("Codacy")?.activate(null)
        }
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(
            CodacyStatusBarWidget::class.java
        )
    }
}