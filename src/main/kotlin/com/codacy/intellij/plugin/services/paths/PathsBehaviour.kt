package com.codacy.intellij.plugin.services.paths

import com.intellij.openapi.project.Project

interface PathsBehaviour {

    fun rootPath(project: Project): String

    fun toCliPath(path: String): String =
        path

    fun fromCliPath(path: String): String =
        path
}