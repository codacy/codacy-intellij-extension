package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.services.api.Api
import com.codacy.intellij.plugin.services.api.models.IssueDetails
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ShowIssueDetailsQuickFix(private val issue: IssueDetails) : LocalQuickFix {

    override fun getName(): String = "See issue details"

    override fun getFamilyName(): String = "Show issue details"

    private fun markdownToHtml(markdown: String): String {
        val options = MutableDataSet()
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(markdown)
        return renderer.render(document)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val api = project.service<Api>()
        GlobalScope.launch {
            try {
                val pattern = api.getPattern(issue.commitIssue.toolInfo.uuid, issue.commitIssue.patternInfo.id).data
//            TODO: remove this call, it should be in the service state
                api.listTools()
                val tool = api.getTool(issue.commitIssue.toolInfo.uuid)
                val curatedExplanation = pattern.explanation?.replace(Regex("^#{1,2}\\s.*\\n"), "")
                ApplicationManager.getApplication().invokeLater {
                    val markdownContent = "## ${pattern.title}\n${pattern.description}\n\n$curatedExplanation\n\n---\nSource: [${tool?.name}](${tool?.documentationUrl})"
                    val htmlContent = markdownToHtml(markdownContent)
                    HtmlDialog(project, htmlContent).showAndGet()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
