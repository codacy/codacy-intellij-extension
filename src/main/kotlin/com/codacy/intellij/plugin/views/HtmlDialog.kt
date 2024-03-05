package com.codacy.intellij.plugin.views

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import javax.swing.JComponent
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.net.URI
import javax.swing.Action

class HtmlDialog(project: Project?, private var htmlContent: String) : DialogWrapper(project) {
    init {
        val styledHtmlContent = """
            <html>
            <head>
                <style>
                body {
                    font-family: 'Sans-Serif';
                    padding: 10px;
                }
                </style>
            </head>
            <body>
                $htmlContent
            </body>
            </html>
        """.trimIndent()
        this.htmlContent = styledHtmlContent
        init()
        title = "Issue Details"
    }

    override fun createCenterPanel(): JComponent {
        val textPane = JTextPane().apply {
            contentType = "text/html"
            text = htmlContent
            isEditable = false
            text = htmlContent
            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI(e.url.toString()))
                    }
                }
            }
        }
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val width = (screenSize.width * 0.5).toInt()
        val height = (screenSize.height * 0.8).toInt()

        val scrollPane = JBScrollPane(textPane).apply { preferredSize = Dimension(width, height) }
        return scrollPane
    }

    override fun getPreferredSize(): Dimension {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        return Dimension((screenSize.width * 0.5).toInt(), (screenSize.height * 0.8).toInt())
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}
