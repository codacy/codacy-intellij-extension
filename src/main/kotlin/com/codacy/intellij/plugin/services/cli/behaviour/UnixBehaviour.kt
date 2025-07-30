package com.codacy.intellij.plugin.services.cli.behaviour

import com.codacy.intellij.plugin.services.cli.CodacyCliBehaviour
import com.codacy.intellij.plugin.services.common.Config
import com.intellij.openapi.project.Project
import java.nio.file.Path

class UnixBehaviour : CodacyCliBehaviour {

    override fun rootPath(project: Project): String {
        return project.basePath ?: throw IllegalStateException("Project base path is not set")
    }

    override fun toCliPath(path: String): String = path

    override fun fromCliPath(path: String): String = path

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
