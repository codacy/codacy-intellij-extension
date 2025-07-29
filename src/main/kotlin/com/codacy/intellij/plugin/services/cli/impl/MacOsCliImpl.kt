package com.codacy.intellij.plugin.services.cli.impl

import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.codacy.intellij.plugin.services.cli.models.ProcessedSarifResult
import com.codacy.intellij.plugin.services.cli.models.Region
import com.codacy.intellij.plugin.services.cli.models.RuleInfo
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_CONFIG_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_DOWNLOAD_LINK
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_SHELL_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_V2_VERSION_ENV_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_DIRECTORY_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_TOOLS_CONFIGS_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_YAML_NAME
import com.codacy.intellij.plugin.views.CodacyCliStatusBarWidget
import com.intellij.notification.NotificationType
import com.intellij.util.io.exists
import com.jetbrains.qodana.sarif.SarifUtil
import com.jetbrains.qodana.sarif.model.Run
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.StringReader
import java.nio.file.Path
import java.nio.file.Paths

abstract class MacOsCliImpl : CodacyCli() {

    private val config = Config.instance
    private var accountToken = config.storedApiToken

    override suspend fun prepareCli(autoInstall: Boolean) {
        var _cliCommand = findCliCommand()

        if (!isCliShellFilePresent()) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLING)

            if (_cliCommand == null) {
                _cliCommand = installCli()
                if (_cliCommand == null) {
                    updateWidgetState(CodacyCliStatusBarWidget.State.ERROR)
                    return
                }
            }

            updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLED)
            cliCommand = _cliCommand
        } else if (cliCommand.isBlank() && isCliShellFilePresent()) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLING)
            if (_cliCommand != null) {
                updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLED)
                cliCommand = _cliCommand
            } else {
                updateWidgetState(CodacyCliStatusBarWidget.State.ERROR)

                notificationManager
                    .createNotification(
                        "Something unexpected went wrong when assigning codacy CLI command",
                        NotificationType.ERROR
                    )
                    .notify(project)
                return
            }
        } else {
            if (_cliCommand != null) {
                cliCommand = _cliCommand
            }
        }

        if (autoInstall && !isCodacySettingsPresent()) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLING)
            val initRes = initialize()
            if (initRes) {
                updateWidgetState(CodacyCliStatusBarWidget.State.INITIALIZED)
            } else {
                updateWidgetState(CodacyCliStatusBarWidget.State.ERROR)
            }
        } else if (isCodacySettingsPresent()) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INITIALIZED)
        }

    }

    override suspend fun installCli(): String? {
        val codacyConfigFullPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME)

        if (!isCodacyDirectoryPresent()) {
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

    private fun installDependencies(): Boolean {
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
            return false
        }

        notificationManager
            .createNotification("Dependencies installed successfully", NotificationType.INFORMATION)
            .notify(project)
        return true
    }

    private fun findCliCommand(): String? {
        val fullPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_SHELL_NAME).toAbsolutePath()

        return if (isCliShellFilePresent()) {
            fullPath.toString()
        } else null
    }

    private suspend fun initialize(): Boolean {
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
                notificationManager
                    .createNotification(
                        "Codacy CLI initialize has failed",
                        error.message ?: error.localizedMessage,
                        NotificationType.ERROR
                    )
                    .notify(project)
                return false
            }

            val result = installDependencies()
            if (!result) {
                return false
            }

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

        return true
    }

    private fun downloadCodacyCli(outputPath: String): Int {
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


    override suspend fun analyze(file: String?, tool: String?): List<ProcessedSarifResult>? {
        prepareCli(true)

        updateWidgetState(CodacyCliStatusBarWidget.State.ANALYZING)

        if (cliCommand.isBlank()) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INITIALIZED)

            notificationManager
                .createNotification(
                    "CLI command not found. Please install the CLI first.",
                    NotificationType.ERROR
                )
                .notify(project)
            return null
        }

        try {
            var results: List<ProcessedSarifResult> = emptyList()

            withTempFile { tempFile, tempFilePath ->

                val command = buildString {
                    append(cliCommand)
                    append(" analyze ")
                    append(" --output ")
                    append(tempFilePath.toString())
                    append(" ")
                    if (file != null) append(file).append(" ")
                    append("--format sarif")
                }

                val params = if (tool != null) mapOf("tool" to tool) else emptyMap()

                runBlocking {
                    val execResult = execAsync(command, params)
                    if (execResult.isFailure) {
                        notificationManager.createNotification(
                            "Codacy CLI analysis has failed",
                            execResult.exceptionOrNull()?.message ?: "Unknown error",
                            NotificationType.ERROR
                        )
                        updateWidgetState(CodacyCliStatusBarWidget.State.ERROR)
                    }
                }

                val fileOutput = File(tempFilePath.toUri()).readText()

                results = fileOutput
                    .let { SarifUtil.readReport(StringReader(it)).runs }
                    ?.let(::processSarifResults)
                    ?: emptyList()

                updateWidgetState(CodacyCliStatusBarWidget.State.INITIALIZED)
            }

            return results
        } catch (error: Exception) {
            notificationManager
                .createNotification(
                    "Codacy CLI analysis has failed",
                    error.message ?: error.localizedMessage,
                    NotificationType.ERROR
                )
            updateWidgetState(CodacyCliStatusBarWidget.State.INITIALIZED)
            return emptyList()
        }
    }


    fun processSarifResults(runs: List<Run>): List<ProcessedSarifResult> {
        return runs.flatMap { run ->
            val tool = run.tool.driver.name
            val rules = run.tool.driver.rules?.associateBy { it.id } ?: emptyMap()

            run.results?.flatMap { result ->
                val rule = result.ruleId?.let { rules[it] }
                val level = result.level ?: "error"
                val message = result.message?.text ?: "No message provided."

                result.locations?.map { location ->
                    val filePath = location.physicalLocation?.artifactLocation?.uri
                    val region = location.physicalLocation?.region?.let {
                        Region(
                            startLine = it.startLine,
                            startColumn = it.startColumn,
                            endLine = it.endLine,
                            endColumn = it.endColumn
                        )
                    }
                    ProcessedSarifResult(
                        tool = tool,
                        rule = rule?.let {
                            RuleInfo(
                                id = it.id,
                                name = it.name,
                                helpUri = it.helpUri.toString(),
                                shortDescription = it.shortDescription?.text
                            )
                        },
                        level = level.toString(),
                        message = message,
                        filePath = filePath,
                        region = region
                    )
                } ?: emptyList()
            } ?: emptyList()
        }
    }

    private fun withTempFile(
        function: (File, Path) -> Unit
    ) {
        val tempFileName = getCodacyCliTempFileName()
        val tempFilePath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, tempFileName)
        val tempFile = tempFilePath.toFile()
        try {
            function(tempFile, tempFilePath)
        } finally {
            tempFile.delete()
        }
    }

}
