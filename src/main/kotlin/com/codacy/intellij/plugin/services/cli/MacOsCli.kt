package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_SHELL_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_DOWNLOAD_LINK
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_V2_VERSION_ENV_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_DIRECTORY_NAME
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import java.io.File
import java.nio.file.Paths

@Service
class MacOsCli() : CodacyCli() {

    private val config = Config.instance

    var accountToken = config.storedApiToken

    fun findCliCommand(project: Project): String? {
        //TODO check unknown project path
        val fullPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_SHELL_NAME).toAbsolutePath()

        return if (isCliShellFilePresent(project)) {
            fullPath.toString()
        } else null
    }

    override suspend fun prepareCli(autoInstall: Boolean) {
        if (cliCommand.isBlank()) {

            var _cliCommand = findCliCommand(project)
            if (_cliCommand == null) {
                //Install CLI 1st
                _cliCommand = installCli()

                if (_cliCommand == null) {
                    //TODO better error handling
                    throw RuntimeException("Failed to install CLI, please check your configuration and try again.")
                }
            }
            cliCommand = _cliCommand
        }

        if (autoInstall && !isCodacySettingsPresent(project)) {
            initialize()
        }
    }

    override suspend fun installCli(): String? {
        val fullPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME)

        if (!isCodacyDirectoryPresent(project)) {
            fullPath.toFile().mkdirs()
        }

        val codacyCliPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_SHELL_NAME)

        if (!codacyCliPath.exists()) {
            //TODO some preflight check for curl?
            val execOutputPath = codacyCliPath.toAbsolutePath().toString()

            //TODO add a check if it downloaded successfully
            downloadCodacyCli(execOutputPath, project)
            return execOutputPath
        }

        return null
    }

    fun installDependencies() {
        val program = ProcessBuilder(cliCommand, "install")
            .redirectErrorStream(true)
        program.environment()[CODACY_CLI_V2_VERSION_ENV_NAME] = config.cliVersion

        val exitCode = program
            .start()
            .waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Curl failed with exit code $exitCode")
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification("Dependencies installed successfully", NotificationType.INFORMATION)
            .notify(project)
    }

    suspend fun initialize() {
        //TODO use new file checking function
        val configFilePath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, "codacy.yaml")
        val cliConfigFilePath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, "cli-config.yaml")
        val toolsFolderPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, "tools-configs")

        val initFilesOk = configFilePath.exists() && cliConfigFilePath.exists() && toolsFolderPath.exists()

        var needsInitialization = !initFilesOk

        if (initFilesOk) {
            //TODO make sure path is okay, maybe needs to be absolute
            //TODO file check function
            val cliConfig =
                File(Paths.get(rootPath, CODACY_DIRECTORY_NAME, "cli-config.yaml").toString()).readText(/*UTF-8*/)

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
        }


    }

    fun downloadCodacyCli(outputPath: String, project: Project) {
        val process = ProcessBuilder("curl", "-Ls", CODACY_CLI_DOWNLOAD_LINK)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Curl failed with exit code $exitCode")
        }

        // Write the output to the specified file
        val outputFile = Paths.get(outputPath)
        outputFile.toFile().writeText(output)

        // Give executable permissions to the file
        ProcessBuilder("chmod", "+x", outputFile.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

}
