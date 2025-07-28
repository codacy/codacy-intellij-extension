package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.cli.behaviour.LinuxBehaviour
import com.codacy.intellij.plugin.services.cli.behaviour.WindowsCliBehaviour
import com.codacy.intellij.plugin.services.cli.models.ProcessedSarifResult
import com.codacy.intellij.plugin.services.cli.models.Region
import com.codacy.intellij.plugin.services.cli.models.RuleInfo
import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_CONFIG_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_SHELL_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_V2_VERSION_ENV_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_DIRECTORY_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_GITIGNORE_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_LOGS_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_TOOLS_CONFIGS_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_YAML_NAME
import com.codacy.intellij.plugin.services.common.GitRemoteParser
import com.codacy.intellij.plugin.services.git.GitProvider
import com.codacy.intellij.plugin.views.CodacyCliStatusBarWidget
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.util.io.exists
import com.intellij.util.io.isFile
import com.jetbrains.qodana.sarif.SarifUtil
import com.jetbrains.qodana.sarif.model.Run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.StringReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class CodacyCli(private val cliBehaviour: CodacyCliBehaviour) {

    var cliCommand: String = ""

    lateinit var provider: String
    lateinit var organization: String
    lateinit var repository: String
    lateinit var project: Project
    lateinit var rootPath: String

    var isServiceInstantiated: Boolean = false

    protected var codacyStatusBarWidget: CodacyCliStatusBarWidget? = null

    private val config = Config.instance
    private var accountToken = config.storedApiToken

    protected val notificationManager = NotificationGroupManager
        .getInstance()
        .getNotificationGroup("CodacyNotifications")

    fun initService(
        provider: String,
        organization: String,
        repository: String,
        project: Project,
    ) {
        val rootPath = project.basePath
            ?: throw IllegalStateException("Project base path is not set")

        //TODO this logic might need to be changed to accommodate
        // provider/org/repo changing
        if (!isServiceInstantiated) {
            this.provider = provider
            this.organization = organization
            this.repository = repository
            this.project = project
            this.rootPath = rootPath
            isServiceInstantiated = true
        }

        val isSettingsPresent = isCodacySettingsPresent()
        val isCliShellFilePresent = isCliShellFilePresent()

        if (isSettingsPresent && isCliShellFilePresent) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INITIALIZED)
        } else if (isCliShellFilePresent) {
            updateWidgetState(CodacyCliStatusBarWidget.State.INSTALLED)
        } else {
            updateWidgetState(CodacyCliStatusBarWidget.State.NOT_INSTALLED)
        }
    }

    companion object {
        fun getService(project: Project): CodacyCli {
            val gitProvider = GitProvider.getRepository(project)
                ?: throw IllegalStateException("No Git provider found for the project")

            val remote = gitProvider.remotes.firstOrNull()
                ?: throw IllegalStateException("No remote found in the Git repository")

            val gitInfo = GitRemoteParser.parseGitRemote(remote.firstUrl!!)

            return getService(
                gitInfo.provider,
                gitInfo.organization,
                gitInfo.repository,
                project
            )
        }


        private fun getService(
            provider: String,
            organization: String,
            repository: String,
            project: Project,
        ): CodacyCli {
            val systemOs = System.getProperty("os.name").lowercase()

            val cli = when {
                systemOs == "mac os x" || systemOs.contains("darwin") -> {
                    CodacyCli(LinuxBehaviour())
                }

                systemOs.contains("windows") -> {
                    val process = ProcessBuilder("wsl", "--status")
                        .redirectErrorStream(true)
                        .start()

                    process.waitFor()

                    val isWSLSupported =
                        process.inputStream.bufferedReader().readText().contains("Default Distribution")

                    if (isWSLSupported) {
                        CodacyCli(WindowsCliBehaviour())
                    } else {
                        notificationGroup.createNotification(
                            "Window Subsystem for Linux detection failure",
                            "WSL not present on this machine.",
                            NotificationType.WARNING
                        )

                        CodacyCli(WindowsCliBehaviour())
                    }
                }

                else -> {
                    throw IllegalStateException("Unsupported OS: $systemOs")
                }
            }

            cli.initService(provider, organization, repository, project)

            return cli
        }
    }

    suspend fun prepareCli(autoInstall: Boolean) {
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

    fun installCli(): String? {
        val codacyConfigFullPath = Paths.get(rootPath, Config.CODACY_DIRECTORY_NAME)

        if (!isCodacyDirectoryPresent()) {
            codacyConfigFullPath.toFile().mkdirs()
        }

        val codacyCliPath =
            Paths.get(rootPath, Config.CODACY_DIRECTORY_NAME, Config.CODACY_CLI_SHELL_NAME).toAbsolutePath()

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
        val program = cliBehaviour.buildCommand(cliCommand, "install").redirectErrorStream(true)
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
        val fullPath = Paths.get(rootPath, Config.CODACY_DIRECTORY_NAME, Config.CODACY_CLI_SHELL_NAME).toAbsolutePath()

        return if (isCliShellFilePresent()) {
            fullPath.toString()
        } else null
    }

    suspend fun initialize(): Boolean {
        val configFilePath = Paths.get(rootPath, Config.CODACY_DIRECTORY_NAME, Config.CODACY_YAML_NAME)
        val cliConfigFilePath = Paths.get(rootPath, Config.CODACY_DIRECTORY_NAME, Config.CODACY_CLI_CONFIG_NAME)
        val toolsFolderPath = Paths.get(rootPath, Config.CODACY_DIRECTORY_NAME, Config.CODACY_TOOLS_CONFIGS_NAME)

        val initFilesOk = configFilePath.exists() && cliConfigFilePath.exists() && toolsFolderPath.exists()

        var needsInitialization = !initFilesOk

        if (initFilesOk) {
            val cliConfig =
                File(
                    Paths.get(rootPath, Config.CODACY_DIRECTORY_NAME, Config.CODACY_CLI_CONFIG_NAME).toString()
                ).readText()

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
        val process = cliBehaviour.downloadCliCommand()
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            return exitCode
        }

        val outputFile = Paths.get(outputPath)
        outputFile.toFile().writeText(output)

        return cliBehaviour.chmodCommand(outputFile)
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

    suspend fun analyze(file: String?, tool: String?): List<ProcessedSarifResult>? {
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
            updateWidgetState(CodacyCliStatusBarWidget.State.INITIALIZED)
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
                        level = level.toString(),
                        message = message,
                        filePath = filePath,
                        region = region
                    )
                } ?: emptyList()
            } ?: emptyList()
        }
    }

    suspend fun execAsync(
        command: String,
        args: Map<String, String>? = null
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val commandParts = buildList {
            addAll(command.split(" ").filter { it.isNotBlank() })
            args?.forEach { (k, v) ->
                add("--$k")
                add(v)
            }
        }

        try {
            val program = cliBehaviour.buildCommand(*commandParts.toTypedArray())
                .directory(File(rootPath))
                .redirectErrorStream(false)

            program.environment()[CODACY_CLI_V2_VERSION_ENV_NAME] = config.cliVersion

            val process = program.start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()

            process.waitFor()

            if (process.exitValue() != 0) {
                Result.failure(Exception(stderr.ifEmpty { "Unknown error" }))
            } else {
                Result.success(Pair(stdout, stderr))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    fun registerWidget(widget: CodacyCliStatusBarWidget) {
        this.codacyStatusBarWidget = widget
    }

    fun updateWidgetState(state: CodacyCliStatusBarWidget.State) {
        codacyStatusBarWidget?.updateStatus(state)
    }

    fun isCodacyDirectoryPresent(): Boolean {
        val codacyDir = Paths.get(rootPath, CODACY_DIRECTORY_NAME)
        return codacyDir.exists() && codacyDir.isDirectory()
    }

    fun isCliShellFilePresent(): Boolean {
        val cliFile = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_SHELL_NAME)
        return cliFile.exists() && cliFile.isFile()
    }

    fun isCodacySettingsPresent(): Boolean {
        val codacyDirectory = Paths.get(rootPath, CODACY_DIRECTORY_NAME)

        val gitignoreFile = codacyDirectory.resolve(CODACY_GITIGNORE_NAME)
        val cliConfigFile = codacyDirectory.resolve(CODACY_CLI_CONFIG_NAME)
        val codacyYamlFile = codacyDirectory.resolve(CODACY_YAML_NAME)
        val logsDirectory = codacyDirectory.resolve(CODACY_LOGS_NAME)
        val toolsConfigsDirectory = codacyDirectory.resolve(CODACY_TOOLS_CONFIGS_NAME)

        return (gitignoreFile.exists() && gitignoreFile.isFile() &&
                cliConfigFile.exists() && cliConfigFile.isFile() &&
                codacyYamlFile.exists() && codacyYamlFile.isFile() &&
                logsDirectory.exists() && logsDirectory.isDirectory() &&
                toolsConfigsDirectory.exists() && toolsConfigsDirectory.isDirectory())
    }

    fun getCodacyCliTempFileName(): String {
        val uuid = UUID.randomUUID().toString()
        return ".analysis-result-$uuid.json"
    }

    private fun withTempFile(function: (File, Path) -> Unit) {
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
