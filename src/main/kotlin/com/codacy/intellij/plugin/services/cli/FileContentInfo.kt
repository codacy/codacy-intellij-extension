package com.codacy.intellij.plugin.services.cli

import com.intellij.psi.PsiFile

data class FileContentInfo(val file: PsiFile, val contentHash: Int)
