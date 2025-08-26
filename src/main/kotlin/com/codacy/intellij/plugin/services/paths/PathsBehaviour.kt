package com.codacy.intellij.plugin.services.paths

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
            val systemOs = System.getProperty("os.name").lowercase()

            val pathsInstance = when {
                systemOs == "mac os x" || systemOs.contains("darwin") -> PathsUnix()

                systemOs == "linux" -> PathsUnix()

                systemOs.contains("windows") -> PathsWindows()

                else -> {
                    throw IllegalStateException("Unsupported OS: $systemOs")
                }
            }

            return pathsInstance
        }
    }
}