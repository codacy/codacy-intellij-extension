package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.codacy.intellij.plugin.services.cli.FileContentInfo
import com.codacy.intellij.plugin.services.cli.models.ProcessedSarifResult
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking

class SarifExternalAnnotator : ExternalAnnotator<FileContentInfo, List<ProcessedSarifResult>>() {

    override fun collectInformation(file: PsiFile): FileContentInfo? {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
        val textHash = document.text.hashCode()
        return FileContentInfo(file, textHash)
    }

    override fun doAnnotate(collectedInfo: FileContentInfo?): List<ProcessedSarifResult>? {
        val file = collectedInfo?.file
        val cli = CodacyCli.Companion.getService(file?.project ?: return null)

        return runBlocking {
            val result = cli.analyze(file.virtualFile.path, null)
            return@runBlocking result
        }
    }

    override fun apply(file: PsiFile, annotationResult: List<ProcessedSarifResult>?, holder: AnnotationHolder) {
        val document = file.viewProvider.document ?: return

        for (result in annotationResult ?: emptyList()) {
            val textRange = convertRegionToTextRange(
                document,
                result.region!!.startLine!!, result.region.startColumn!!,
                result.region.endLine!!, result.region.endColumn!!
            )

            holder.newAnnotation(HighlightSeverity.ERROR, result.message)
                .range(textRange)
                .create()
        }
    }

    private fun convertRegionToTextRange(
        document: Document,
        startLine: Int,
        startCol: Int,
        endLine: Int,
        endCol: Int
    ): TextRange {
        try {
            val startOffset: Int = document.getLineStartOffset(startLine - 1) + (startCol - 1)
            val endOffset: Int = document.getLineStartOffset(endLine - 1) + (endCol - 1)
            return TextRange(startOffset, endOffset)
        } catch (e: IndexOutOfBoundsException) {
            return TextRange.EMPTY_RANGE
        }
    }

}
