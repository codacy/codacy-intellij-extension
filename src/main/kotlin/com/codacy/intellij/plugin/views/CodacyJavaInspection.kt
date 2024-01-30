package com.codacy.intellij.plugin.views

import com.intellij.codeInspection.*
import com.intellij.psi.*
import com.intellij.openapi.components.service
import com.codacy.intellij.plugin.services.git.RepositoryManager
import com.codacy.intellij.plugin.services.git.PullRequest
import com.intellij.openapi.util.TextRange

class CodacyJavaInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun getGroupDisplayName(): String = "Codacy"

    override fun getDisplayName(): String = "Codacy Java inspection"

    override fun getShortName(): String = "CodacyJava"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitFile(file: PsiFile) {
//        TODO: merge with CodacyInspection
                println("visitFile $file")
                super.visitFile(file)
                val project = file.project
                val repositoryManager = project.service<RepositoryManager>()
                val pr: PullRequest = repositoryManager.pullRequest ?: return
                val projectBasePath = file.project.basePath ?: return
                val filePath = file.virtualFile.path.removePrefix("$projectBasePath/")
                val sortedIssues = pr.issues.filter { it.commitIssue.filePath == filePath }
                    .sortedBy { it.commitIssue.lineNumber }
                sortedIssues.forEach { issue ->
                    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return@forEach
                    val originalLineNumber = issue.commitIssue.lineNumber - 1
                    var actualLineNumber = originalLineNumber

                    val originalLineText = document.getText(TextRange(document.getLineStartOffset(originalLineNumber), document.getLineEndOffset(originalLineNumber)))
                    if (originalLineText != issue.commitIssue.lineText) {
                        val totalLines = document.lineCount
                        actualLineNumber = (0 until totalLines).find { lineNumber ->
                            val lineText = document.getText(TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber)))
                            lineText == issue.commitIssue.lineText
                        } ?: return@forEach
                    }

                    val lineStartOffset = document.getLineStartOffset(actualLineNumber)
                    val lineEndOffset = document.getLineEndOffset(actualLineNumber)
                    // val startCol = document.getText(TextRange(lineStartOffset, lineEndOffset)).indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: 0
                    var description = "[${issue.commitIssue.patternInfo.category}]"
                    if (!issue.commitIssue.patternInfo.subCategory.isNullOrEmpty()) description = "$description - ${issue.commitIssue.patternInfo.subCategory}"
                    description = "$description ${issue.commitIssue.message}. Codacy [${issue.commitIssue.toolInfo.name}] (${issue.commitIssue.patternInfo.id})"
                    // TODO: REMOVE test
                    // issue.commitIssue.suggestion = " ".repeat(startCol) + "CODACY SUGGESTION PLACEHOLDER"
                    val openFileAndReplaceQuickFix: OpenFileAndReplaceQuickFix? = if (issue.commitIssue.suggestion.isNullOrEmpty()) {
                        null
                    } else {
                        OpenFileAndReplaceQuickFix(file.virtualFile, actualLineNumber,
                            issue.commitIssue.suggestion!!)
                    }
                    val showIssueDetailsQuickFix = ShowIssueDetailsQuickFix(issue)

                    val quickFixes = listOfNotNull(openFileAndReplaceQuickFix, showIssueDetailsQuickFix).toTypedArray()
                    println("adding problem descriptor file $file lineStartOffset $lineStartOffset lineEndOffset $lineEndOffset description $description severityLevel ${issue.commitIssue.patternInfo.severityLevel} quickFixes ${quickFixes.size}")
                    val problemDescriptor = holder.manager.createProblemDescriptor(
                        file,
                        TextRange(lineStartOffset, lineEndOffset),
                        description,
                        when (issue.commitIssue.patternInfo.severityLevel) {
                            "Error" -> ProblemHighlightType.ERROR
                            "Warning" -> ProblemHighlightType.WARNING
                            "Info" -> ProblemHighlightType.WEAK_WARNING
                            else -> ProblemHighlightType.ERROR
                        },
                        false,
                        *quickFixes
                    )
                    println("problemDescriptor $problemDescriptor")
                    holder.registerProblem(problemDescriptor)
                }
            }
        }
    }
}
