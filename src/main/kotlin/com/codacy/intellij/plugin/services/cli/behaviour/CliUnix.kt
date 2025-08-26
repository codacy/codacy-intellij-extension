package com.codacy.intellij.plugin.services.cli.behaviour

import com.codacy.intellij.plugin.services.cli.CodacyCliBehaviour

class CliUnix : CodacyCliBehaviour {

    override fun buildCommand(vararg commandParts: String): ProcessBuilder {
        return ProcessBuilder(*commandParts)
    }
}
