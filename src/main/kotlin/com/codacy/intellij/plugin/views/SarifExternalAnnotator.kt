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
import java.util.concurrent.ConcurrentHashMap


private val resultCache = ConcurrentHashMap<Int, List<ProcessedSarifResult>?>()
private val runningHashes = ConcurrentHashMap.newKeySet<Int>()

class SarifExternalAnnotator : ExternalAnnotator<FileContentInfo, List<ProcessedSarifResult>>() {

    override fun collectInformation(file: PsiFile): FileContentInfo? {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
        val textHash = document.text.hashCode()
        return FileContentInfo(file, textHash)
    }

    override fun doAnnotate(collectedInfo: FileContentInfo?): List<ProcessedSarifResult>? {
        if (collectedInfo == null) return null

        val hash = collectedInfo.contentHash
        resultCache[hash]?.let { return it }

        if (!runningHashes.add(hash)) return null

        try {
            val file = collectedInfo.file
            val cli = CodacyCli.getService(file.project)
            val result = runBlocking {
                cli.analyze(file.virtualFile.path, null)
            }
            resultCache[hash] = result
            return result
        } finally {
            runningHashes.remove(hash)
        }
    }

    override fun apply(file: PsiFile, annotationResult: List<ProcessedSarifResult>?, holder: AnnotationHolder) {
        val document = file.viewProvider.document ?: return

        for (result in annotationResult.orEmpty()) {
            val region = result.region ?: continue

            val textRange = convertRegionToTextRange(
                document,
                region.startLine!!, region.startColumn!!,
                region.endLine!!, region.endColumn!!
            )

            if (textRange.isEmpty.not()) {
                holder.newAnnotation(HighlightSeverity.ERROR, result.message)
                    .range(textRange)
                    .create()
            }
        }
    }

    private fun convertRegionToTextRange(
        document: Document,
        startLine: Int,
        startCol: Int,
        endLine: Int,
        endCol: Int
    ): TextRange {
        return try {
            val startOffset = document.getLineStartOffset(startLine - 1) + (startCol - 1)
            val endOffset = document.getLineStartOffset(endLine - 1) + (endCol - 1)
            TextRange(startOffset, endOffset)
        } catch (e: IndexOutOfBoundsException) {
            TextRange.EMPTY_RANGE
        }
    }

}
