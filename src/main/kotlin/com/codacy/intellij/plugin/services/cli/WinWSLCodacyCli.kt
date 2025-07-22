package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.cli.impl.MacOsCliImpl
import com.codacy.intellij.plugin.services.cli.models.ProcessedSarifResult
import com.intellij.openapi.components.Service

@Service
class WinWSLCodacyCli : MacOsCliImpl() {
    companion object {
        fun toWSLPath(path: String): String =
            path.replace("\\", "/").replace(Regex("^([a-zA-Z]):"), "/mnt/$1")

        fun fromWSLPath(path: String): String =
            path.replace(Regex("^/mnt/([a-zA-Z])"), "$1:").replace("/", "\\")
    }

    override suspend fun prepareCli(autoInstall: Boolean) {
        // Use MacOsCli logic, but ensure cliCommand is WSL path
        if (cliCommand.isBlank()) {
            val fullPath = toWSLPath("$rootPath/.codacy/cli.sh")
            cliCommand = fullPath
        }

        // The rest is as per MacOsCli
        super.prepareCli(autoInstall)
    }

    override suspend fun installCli(): String? {
        // Use MacOsCli logic, but return WSL path
        return super.installCli()?.let { toWSLPath(it) }
    }

    override suspend fun analyze(file: String?, tool: String?): List<ProcessedSarifResult>? {
        // Use WSL path for file
        val wslFile = file?.let { toWSLPath(it) }
        return super.analyze(wslFile, tool)
    }

//    override suspend fun execAsync(command: String, args: Map<String, String>?): Result<Pair<String, String>> {
//        // Prepend wsl to all commands
//        return super.execAsync("wsl $command", args)
//    }
}
