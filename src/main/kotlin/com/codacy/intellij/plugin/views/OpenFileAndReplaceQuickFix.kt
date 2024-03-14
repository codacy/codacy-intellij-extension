package com.codacy.plugin.views

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class OpenFileAndReplaceQuickFix(
    private val virtualFile: VirtualFile,
    private val lineNumber: Int,
    private val replacementText: String,
) : LocalQuickFix {
    override fun getName(): String = "Apply Codacy suggestion"

    override fun getFamilyName(): String = "Apply Codacy's suggested fix"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        ApplicationManager.getApplication().invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val openFileDescriptor = OpenFileDescriptor(project, virtualFile, lineNumber, 0)
            val editor = fileEditorManager.openTextEditor(openFileDescriptor, true)

            val document = editor?.document
            WriteCommandAction.runWriteCommandAction(project) {
                val startOffset = document?.getLineStartOffset(lineNumber) ?: 0
                val endOffset = document?.getLineEndOffset(lineNumber) ?: startOffset
                document?.replaceString(startOffset, endOffset, replacementText)
            }
        }
    }
}
