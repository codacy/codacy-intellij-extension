package com.codacy.intellij.plugin.services.cli.impl

import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.codacy.intellij.plugin.services.cli.CodacyCliStatusBarWidget
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
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import com.jetbrains.qodana.sarif.SarifUtil
import com.jetbrains.qodana.sarif.model.Run
import java.io.File
import java.io.StringReader
import java.nio.file.Paths

abstract class MacOsCliImpl : CodacyCli() {

    private val config = Config.instance
    private var accountToken = config.storedApiToken

    fun findCliCommand(project: Project): String? {
        val fullPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_SHELL_NAME).toAbsolutePath()

        return if (isCliShellFilePresent(project)) {
            fullPath.toString()
        } else null
    }

    override suspend fun prepareCli(autoInstall: Boolean) {
        var _cliCommand = findCliCommand(project)

        if (/*cliCommand.isBlank() &&*/ !isCliShellFilePresent(project)) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLING)

            if (_cliCommand == null) {
                _cliCommand = installCli()
                if (_cliCommand == null) {
                    updateWidgetState(CodacyCliStatusBarWidget.State.ERROR)
                    return
                }
                notificationManager.createNotification("STATE", "INSTALLED", NotificationType.INFORMATION)
                    .notify(project)
            }

            updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLED)
            cliCommand = _cliCommand
        } else if(cliCommand.isBlank() && isCliShellFilePresent(project)) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLING)
            if (_cliCommand != null) {
                updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLED)
                cliCommand = _cliCommand
            } else {
                updateWidgetState(CodacyCliStatusBarWidget.State.ERROR)
                return
            }
        } else {
            if (_cliCommand != null) {
                cliCommand = _cliCommand
            }
        }

        if (autoInstall && !isCodacySettingsPresent(project)) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLING)
            val initRes = initialize()
            if (initRes) {
                updateWidgetState(CodacyCliStatusBarWidget.State.INITIALIZED)
            } else {
                updateWidgetState(CodacyCliStatusBarWidget.State.ERROR)
            }
        } else if (isCodacySettingsPresent(project)) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INITIALIZED)
        }

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

    fun installDependencies(): Boolean {
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
//                updateWidgetState(CodacyCliStatusBarWidget.State.ERROR)
//                throw RuntimeException("Failed to initialize CLI: $error")
                return false //TODO error message?
            }

            // install dependencies
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

//        updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLED)
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

        if (cliCommand.isBlank()) {
            throw Exception("CLI command not found. Please install the CLI first.")
        }

        try {
            val command = buildString {
                append(cliCommand)
                append(" analyze ")
                if (file != null) append(file).append(" ")
                append("--format sarif")
            }

            val params = if (tool != null) mapOf("tool" to tool) else emptyMap()
            val execResult = execAsync(command, params)

            val (stdout, stderr) = execResult.getOrElse { throw it }

            val jsonMatch = Regex("""(\{[\s\S]*\}|\[[\s\S]*\])""").find(stdout)?.value

            val results = jsonMatch
                ?.let { SarifUtil.readReport(StringReader(it)).runs }
                ?.let(::processSarifResults)
                ?: emptyList()

            notificationManager.createNotification(
                "Codacy CLI analyzed processed FINISH",
                results.toString(),
                NotificationType.INFORMATION
            ).notify(project)

            return results
        } catch (error: Exception) {
            throw error
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
                        level = level.toString(), //TODO check if correct
                        message = message,
                        filePath = filePath,
                        region = region
                    )
                } ?: emptyList()
            } ?: emptyList()
        }
    }
}
