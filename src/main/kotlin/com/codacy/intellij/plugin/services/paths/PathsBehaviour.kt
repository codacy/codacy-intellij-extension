package com.codacy.intellij.plugin.services.paths

import com.codacy.intellij.plugin.services.common.OsType
import com.codacy.intellij.plugin.services.common.SystemDetectionService
import com.codacy.intellij.plugin.services.paths.behaviour.PathsUnix
import com.codacy.intellij.plugin.services.paths.behaviour.PathsWindows
import com.intellij.openapi.project.Project

interface PathsBehaviour {

    fun rootPath(project: Project): String

    fun toCliPath(path: String): String =
        path

    fun fromCliPath(path: String): String =
        path

    object Factory {
        fun build(): PathsBehaviour {
            val osType = SystemDetectionService.detectOs()

            val pathsInstance = when {
                osType == OsType.MacOS -> PathsUnix()
                osType == OsType.Linux -> PathsUnix()
                osType == OsType.Windows -> PathsWindows()
                else -> throw IllegalStateException("Unsupported OS: $osType")
            }

            return pathsInstance
        }
    }
}
