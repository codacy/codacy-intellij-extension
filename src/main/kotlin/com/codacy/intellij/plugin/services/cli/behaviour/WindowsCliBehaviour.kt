package com.codacy.intellij.plugin.services.cli.behaviour

import com.codacy.intellij.plugin.services.cli.CodacyCliBehaviour
import com.codacy.intellij.plugin.services.common.Config
import java.nio.file.Path

class WindowsCliBehaviour: CodacyCliBehaviour {

    override fun toCliPath(path: String): String =
        path.replace("\\", "/").replace(Regex("^([a-zA-Z]):"), "/mnt/$1")

    override fun fromCliPath(path: String): String =
        path.replace(Regex("^/mnt/([a-zA-Z])"), "$1:").replace("/", "\\")

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
