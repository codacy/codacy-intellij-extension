package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.cli.impl.MacOsCliImpl
import com.codacy.intellij.plugin.services.cli.models.ProcessedSarifResult
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_DOWNLOAD_LINK
import com.intellij.openapi.components.Service

@Service
class WinWSLCodacyCli : MacOsCliImpl() {
    companion object {
        fun toWSLPath(path: String): String =
            path.replace("\\", "/").replace(Regex("^([a-zA-Z]):"), "/mnt/$1")

        fun fromWSLPath(path: String): String =
            path.replace(Regex("^/mnt/([a-zA-Z])"), "$1:").replace("/", "\\")
    }

    override fun initService(
        provider: String,
        organization: String,
        repository: String,
        project: com.intellij.openapi.project.Project,
    ) {
        val basePath = project.basePath
        val winRootPath = if (basePath != null && basePath.startsWith("/mnt/")) {
            fromWSLPath(basePath)
        } else {
            basePath
        }

        if (!isServiceInstantiated) {
            this.provider = provider
            this.organization = organization
            this.repository = repository
            this.project = project
            this.rootPath = winRootPath ?: throw IllegalStateException("Project base path is not set")
            isServiceInstantiated = true
        }
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

    override suspend fun execAsync(command: String, args: Map<String, String>?): Result<Pair<String, String>> {
        // Prepend wsl as a separate argument
        val commandList = buildList {
            add("wsl")
            addAll(command.split(" ").filter { it.isNotBlank() })
            args?.forEach { (k, v) ->
                add("--$k")
                add(v)
            }
        }

        return super.execAsync(commandList.joinToString(" "), emptyMap())
    }

    // Override to use WSL for download and chmod
    override fun getDownloadCommand(): List<String> =
        listOf("wsl", "curl", "-Ls", CODACY_CLI_DOWNLOAD_LINK)

    override fun getChmodCommand(outputPath: String): List<String> =
        listOf("wsl", "chmod", "+x", outputPath)
}
