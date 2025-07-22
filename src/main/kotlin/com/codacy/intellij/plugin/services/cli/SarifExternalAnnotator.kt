package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.cli.models.ProcessedSarifResult
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking

class SarifExternalAnnotator : ExternalAnnotator<PsiFile, List<ProcessedSarifResult>>() {

    override fun collectInformation(file: PsiFile): PsiFile? {
        return file
    }

    override fun doAnnotate(collectedInfo: PsiFile?): List<ProcessedSarifResult>? {
        val cli = CodacyCli.getService(collectedInfo?.project ?: return null)


        return runBlocking {
            val result = cli.analyze(collectedInfo.virtualFile.path, null)
            return@runBlocking result
        }
    }

    override fun apply(file: PsiFile, annotationResult: List<ProcessedSarifResult>?, holder: AnnotationHolder) {
        val document = file.viewProvider.document ?: return

        for( result in annotationResult ?: emptyList()) {
            val textRange = convertRegionToTextRange(document,
                result.region!!.startLine!!, result.region.startColumn!!,
                result.region.endLine!!, result.region.endColumn!!)

            holder.newAnnotation(HighlightSeverity.ERROR, result.message)
                .range(textRange)
                .create()
        }
    }

    fun convertRegionToTextRange(
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
