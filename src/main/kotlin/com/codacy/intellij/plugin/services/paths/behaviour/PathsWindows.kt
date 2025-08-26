package com.codacy.intellij.plugin.services.paths.behaviour

import com.codacy.intellij.plugin.services.paths.PathsBehaviour
import com.intellij.openapi.project.Project
import kotlin.text.startsWith

class PathsWindows : PathsBehaviour {

    override fun rootPath(project: Project): String {
        val basePath = project.basePath

        val winRootPath =
            if (basePath != null && basePath.startsWith("/mnt/")) {
                fromCliPath(basePath)
            } else {
                basePath
            }

        return winRootPath ?: throw IllegalStateException("Project base path is not set")
    }

    override fun toCliPath(path: String): String =
        path.replace("\\", "/")
            .replace(Regex("^([a-zA-Z]):"), "/mnt/$1")
            .replace(Regex("^/mnt/([a-zA-Z])")) { "/mnt/${it.groupValues[1].lowercase()}" }

    override fun fromCliPath(path: String): String =
        path.replace(Regex("^/mnt/([a-zA-Z])"), "$1:")
            .replace("/", "\\")
            .replaceFirstChar { it.uppercaseChar() }
}