package com.codacy.intellij.plugin.services.cli.behaviour

import com.codacy.intellij.plugin.services.cli.CodacyCliBehaviour
import com.codacy.intellij.plugin.services.common.Config
import java.nio.file.Path

class LinuxBehaviour: CodacyCliBehaviour {

    override fun downloadCliCommand(): ProcessBuilder {
        return ProcessBuilder("curl", "-Ls", Config.CODACY_CLI_DOWNLOAD_LINK)
    }

    override fun chmodCommand(outputPath: Path): ProcessBuilder {
        return ProcessBuilder("chmod", "+x", outputPath.toAbsolutePath().toString())
    }

    override fun buildCommand(vararg commandParts: String): ProcessBuilder {
        return ProcessBuilder(*commandParts)
    }
}
