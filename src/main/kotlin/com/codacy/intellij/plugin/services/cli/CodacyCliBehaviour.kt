package com.codacy.intellij.plugin.services.cli

import com.intellij.openapi.project.Project
import java.nio.file.Path

interface CodacyCliBehaviour {
    fun rootPath(project: Project): String
    fun toCliPath(path: String): String = path
    fun fromCliPath(path: String): String = path

    fun downloadCliCommand(): ProcessBuilder
    fun chmodCommand(outputPath: Path): ProcessBuilder
    fun buildCommand(vararg commandParts: String): ProcessBuilder
}
