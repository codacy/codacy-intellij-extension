package com.codacy.intellij.plugin.services.cli

import java.nio.file.Path

interface CodacyCliBehaviour {
    fun downloadCliCommand(): ProcessBuilder
    fun chmodCommand(outputPath: Path): ProcessBuilder
    fun buildCommand(vararg commandParts: String): ProcessBuilder
}
