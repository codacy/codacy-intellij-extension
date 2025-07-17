package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_CONFIG_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_DOWNLOAD_LINK
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_SHELL_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_V2_VERSION_ENV_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_DIRECTORY_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_TOOLS_CONFIGS_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_YAML_NAME
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import java.io.File
import java.nio.file.Paths

@Service
class MacOsCli() : CodacyCli() {

    private val config = Config.instance
    private var accountToken = config.storedApiToken

    fun findCliCommand(project: Project): String? {
        val fullPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_SHELL_NAME).toAbsolutePath()

        return if (isCliShellFilePresent(project)) {
            fullPath.toString()
        } else null
    }

    override suspend fun prepareCli(autoInstall: Boolean) {
        if (cliCommand.isBlank()) {

            var _cliCommand = findCliCommand(project)
            if (_cliCommand == null) {

                _cliCommand = installCli()

                if (_cliCommand == null) {
                    return
                }
            }
            cliCommand = _cliCommand
        }

        if (autoInstall && !isCodacySettingsPresent(project)) {
            initialize()
        }

        cliWindowFactory.updateCliStatus(
            isCliShellFilePresent(project),
            isCodacySettingsPresent(project)
        )
    }

    override suspend fun installCli(): String? {
        val codacyConfigFullPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME)

        if (!isCodacyDirectoryPresent(project)) {
            codacyConfigFullPath.toFile().mkdirs()
        }

        val codacyCliPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_SHELL_NAME).toAbsolutePath()

        if (!codacyCliPath.exists()) {
            val cliExitCode = downloadCodacyCli(codacyCliPath.toString())
            if (cliExitCode != 0) {
                notificationManager
                    .createNotification(
                        "Failed to download Codacy CLI",
                        "Please check your network connection and try again. Exited with error code. $cliExitCode",
                        NotificationType.ERROR
                    )
                    .notify(project)
                return null
            } else {
                return codacyCliPath.toString()
            }
        } else {
            return codacyCliPath.toString()
        }
    }

    fun installDependencies() {
        val program = ProcessBuilder(cliCommand, "install")
            .redirectErrorStream(true)
        program.environment()[CODACY_CLI_V2_VERSION_ENV_NAME] = config.cliVersion

        val exitCode = program
            .start()
            .waitFor()

        if (exitCode != 0) {
            notificationManager
                .createNotification(
                    "Codacy CLI has failed to install Dependencies",
                    "Program exited with error code $exitCode",
                    NotificationType.ERROR
                )
                .notify(project)
        }

        notificationManager
            .createNotification("Dependencies installed successfully", NotificationType.INFORMATION)
            .notify(project)
    }

    suspend fun initialize() {
        val configFilePath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_YAML_NAME)
        val cliConfigFilePath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_CONFIG_NAME)
        val toolsFolderPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_TOOLS_CONFIGS_NAME)

        val initFilesOk = configFilePath.exists() && cliConfigFilePath.exists() && toolsFolderPath.exists()

        var needsInitialization = !initFilesOk

        if (initFilesOk) {
            val cliConfig =
                File(Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_CONFIG_NAME).toString()).readText()

            if ((cliConfig == "mode: local" && this.repository.isNotBlank()) || (cliConfig == "mode: remote" && this.repository.isBlank())) {
                needsInitialization = true
            }
        }

        if (needsInitialization) {
            val initParams = if (
                this.accountToken?.isNotBlank() == true &&
                this.repository.isNotBlank() &&
                this.provider.isNotBlank() &&
                this.organization.isNotBlank()
            ) {
                mapOf(
                    "provider" to this.provider,
                    "organization" to this.organization,
                    "repository" to this.repository,
                    "api-token" to this.accountToken!!
                )
            } else {
                emptyMap()
            }

            try {
                execAsync("$cliCommand init", initParams)
            } catch (error: Exception) {
                throw RuntimeException("Failed to initialize CLI: $error")
            }

            // install dependencies
            installDependencies()

            // add cli.sh to .gitignore
            val gitignorePath = Paths.get(rootPath, ".codacy", ".gitignore")
            val gitignoreFile = gitignorePath.toFile()
            if (!gitignoreFile.exists()) {
                gitignoreFile.writeText("*.sh\n")
            } else {
                val gitignoreContent = gitignoreFile.readText(Charsets.UTF_8)
                if (!gitignoreContent.contains("*.sh")) {
                    gitignoreFile.appendText("*.sh\n")
                }
            }

            notificationManager.createNotification("Codacy CLI initialized successfully", NotificationType.INFORMATION)
                .notify(project)
        }
    }

    fun downloadCodacyCli(outputPath: String): Int {
        val process = ProcessBuilder("curl", "-Ls", CODACY_CLI_DOWNLOAD_LINK)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            return exitCode
        }

        val outputFile = Paths.get(outputPath)
        outputFile.toFile().writeText(output)

        return ProcessBuilder("chmod", "+x", outputFile.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

}
