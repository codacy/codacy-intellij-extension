package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.views.CodacyCliToolWindowFactory
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.notification.NotificationType
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import kotlinx.coroutines.runBlocking
import com.intellij.openapi.editor.Document

class SarifExternalAnnotator : ExternalAnnotator<PsiFile, List<ProcessedSarifResult>>() {

//    lateinit var project: Project
//    lateinit var cliToolWindowFactory: CodacyCliToolWindowFactory

//    val cli = CodacyCli.getService("gh", "", "sandbox", project, cliToolWindowFactory)

    override fun collectInformation(file: PsiFile): PsiFile? {
        notificationGroup.createNotification("collectInformation", "", NotificationType.INFORMATION)
            .notify(file?.project)

        return file
    }

    override fun doAnnotate(collectedInfo: PsiFile?): List<ProcessedSarifResult>? {
        notificationGroup.createNotification("analyze test", "", NotificationType.INFORMATION)
            .notify(collectedInfo?.project)

        val cli = CodacyCli.getService("gh", "codacy", "codacy-intellij-plugin", collectedInfo?.project ?: return null)


        return runBlocking {
            val result = cli.analyze(collectedInfo.virtualFile.path, null)
            notificationGroup.createNotification("SUCCESSSSSSSSS", result.toString(), NotificationType.INFORMATION)
                .notify(collectedInfo?.project)
            return@runBlocking result
        }
    }

    override fun apply(file: PsiFile, annotationResult: List<ProcessedSarifResult>?, holder: AnnotationHolder) {
        notificationGroup.createNotification("apply", "", NotificationType.INFORMATION)
            .notify(file?.project)
        val document = file.viewProvider.document ?: return

        for( result in annotationResult ?: emptyList()) {
            val textRange = convertRegionToTextRange(document,
                result.region!!.startLine!!, result.region.startColumn!!,
                result.region.endLine!!, result.region.endColumn!!)

            notificationGroup.createNotification("textrange", result.region.toString(), NotificationType.INFORMATION)
                .notify(file?.project)

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
            // Handle edge case: invalid line or column
            return TextRange.EMPTY_RANGE
        }
    }

}
