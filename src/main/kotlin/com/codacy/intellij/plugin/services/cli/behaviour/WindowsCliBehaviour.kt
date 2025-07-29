package com.codacy.intellij.plugin.services.cli.behaviour

import com.codacy.intellij.plugin.services.cli.CodacyCliBehaviour
import com.codacy.intellij.plugin.services.common.Config
import com.intellij.openapi.project.Project
import java.nio.file.Path

class WindowsCliBehaviour : CodacyCliBehaviour {

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

    override fun downloadCliCommand(): ProcessBuilder {
        return ProcessBuilder("wsl", "curl", "-Ls", Config.CODACY_CLI_DOWNLOAD_LINK)
    }

    override fun chmodCommand(outputPath: Path): ProcessBuilder {
        return ProcessBuilder("wsl", "chmod", "+x", outputPath.toAbsolutePath().toString())
    }

    override fun buildCommand(vararg commandParts: String): ProcessBuilder {
        return ProcessBuilder("wsl", *commandParts)
    }
}
