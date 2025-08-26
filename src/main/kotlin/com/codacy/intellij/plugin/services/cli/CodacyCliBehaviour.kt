package com.codacy.intellij.plugin.services.cli

interface CodacyCliBehaviour {
    fun buildCommand(vararg commandParts: String): ProcessBuilder
}
