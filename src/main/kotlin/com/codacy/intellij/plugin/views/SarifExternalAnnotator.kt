package com.codacy.intellij.plugin.views

import com.codacy.intellij.plugin.services.cli.CodacyCliService
import com.codacy.intellij.plugin.services.cli.FileContentInfo
import com.codacy.intellij.plugin.services.cli.models.ProcessedSarifResult
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

private val resultCache = ConcurrentHashMap<Int, List<ProcessedSarifResult>?>()
private val runningHashes = ConcurrentHashMap.newKeySet<Int>()

class SarifExternalAnnotator : ExternalAnnotator<FileContentInfo, List<ProcessedSarifResult>>() {

    companion object {
        private val logger = Logger.getInstance(SarifExternalAnnotator::class.java)
    }

    override fun collectInformation(file: PsiFile): FileContentInfo? {
        val cliService = CodacyCliService.getService(file.project)
        if (cliService.codacyCliState != CodacyCliService.CodacyCliState.INITIALIZED) {
            return null
        }

        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
        val textHash = document.text.hashCode()
        logger.info("Collecting information for file: ${file.virtualFile.path}, hash: $textHash")
        return FileContentInfo(file, textHash)
    }

    override fun doAnnotate(collectedInfo: FileContentInfo?): List<ProcessedSarifResult>? {
        logger.info("Running annotation for file: ${collectedInfo?.file?.virtualFile?.path}, hash: ${collectedInfo?.contentHash}")
        if (collectedInfo == null) return null

        val hash = collectedInfo.contentHash
        resultCache[hash]?.let {
            logger.info("File has cached results, it will be returned")
            return it
        }

        if (!runningHashes.add(hash)) {
            return null
        }

        try {
            logger.info("Running analysis for file: ${collectedInfo.file.virtualFile.path}, hash: $hash")
            val file = collectedInfo.file
            val cli = CodacyCliService.getService(file.project)
            val result = runBlocking {
                cli.analyze(file.virtualFile.path, null)
            }
            logger.info("Analysis finished for file: ${file.virtualFile.path}, hash: $hash, result size: ${result?.size ?: 0} -- message ${result?.firstOrNull()?.message.toString()}")

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
            val startLine = region.startLine ?: continue
            val startColumn = region.startColumn ?: 1

            val textRange: TextRange = try {
                if (region.endLine != null && region.endColumn != null) {
                    logger.info("Creating text range for multi-line region: startLine=$startLine, startColumn=$startColumn, endLine=${region.endLine}, endColumn=${region.endColumn}")
                    getTextRange(document, startLine, startColumn, region.endLine, region.endColumn)
                } else {
                    logger.info("Creating text range for single point: startLine=$startLine, startColumn=$startColumn")
                    getTextRangeForSinglePoint(document, startLine, startColumn)
                }
            } catch (e: IndexOutOfBoundsException) {
                logger.error("Index out of bounds exception when creating text range.", e)
                continue
            }

            if (!textRange.isEmpty) {
                holder.newAnnotation(HighlightSeverity.ERROR, result.message)
                    .range(textRange)
                    .create()
            }
        }
    }

    private fun getTextRangeForSinglePoint(
        document: Document,
        startLine: Int,
        startCol: Int
    ): TextRange {
        val startLineIndex = startLine - 1
        if (startLineIndex !in 0 until document.lineCount) return TextRange.EMPTY_RANGE
        val startOffset = document.getLineStartOffset(startLineIndex) + (startCol - 1).coerceAtLeast(0)
        val lineEndOffset = document.getLineEndOffset(startLineIndex)
        return TextRange(startOffset, lineEndOffset.coerceAtLeast(startOffset + 1))
    }

    private fun getTextRange(
        document: Document,
        startLine: Int,
        startCol: Int,
        endLine: Int,
        endCol: Int
    ): TextRange {
        try {
            val lineCount = document.lineCount
            val safeStartLine = (startLine - 1).coerceIn(0, lineCount - 1)
            val safeEndLine = (endLine - 1).coerceIn(0, lineCount - 1)

            val safeStartOffset = document.getLineStartOffset(safeStartLine) + (startCol - 1).coerceAtLeast(0)
            val safeEndOffset = document.getLineStartOffset(safeEndLine) + (endCol - 1).coerceAtLeast(0)

            return if (safeStartOffset >= safeEndOffset) {
                TextRange(safeStartOffset, safeStartOffset + 1)
            } else {
                TextRange(safeStartOffset, safeEndOffset)
            }
        } catch (e: IndexOutOfBoundsException) {
            logger.error("Index out of bounds exception when creating text range.", e)
            return TextRange.EMPTY_RANGE
        }
    }
}
