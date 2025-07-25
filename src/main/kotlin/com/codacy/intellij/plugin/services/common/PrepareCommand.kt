package com.codacy.intellij.plugin.services.common

object PrepareCommand {

    operator fun invoke(command: String) =
        invoke(*command.split("\\s+".toRegex()).toTypedArray())

    operator fun invoke(vararg commands: String): ProcessBuilder {
        return ProcessBuilder(commands.flatMap {
            it.split("\\s+".toRegex())
        }).redirectErrorStream(true)
    }
}