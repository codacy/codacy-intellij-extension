package com.codacy.intellij.plugin.services.common

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object IconUtils {
    val CodacyIcon: Icon by lazy {
        IconLoader.getIcon("/icons/codacy-logo.svg", IconUtils::class.java)
    }
}
