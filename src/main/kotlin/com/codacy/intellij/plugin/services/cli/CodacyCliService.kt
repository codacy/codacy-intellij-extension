package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.listeners.ServiceState
import com.codacy.intellij.plugin.listeners.WidgetStateListener
import com.codacy.intellij.plugin.services.agent.model.Provider
import com.codacy.intellij.plugin.services.cli.behaviour.CliUnix
import com.codacy.intellij.plugin.services.cli.behaviour.CliWindows
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
import com.codacy.intellij.plugin.services.common.IconUtils
import com.codacy.intellij.plugin.services.git.GitProvider
import com.codacy.intellij.plugin.services.paths.PathsBehaviour
import com.codacy.intellij.plugin.services.paths.behaviour.PathsUnix
import com.codacy.intellij.plugin.services.paths.behaviour.PathsWindows
import com.codacy.intellij.plugin.telemetry.CliInstallEvent
import com.codacy.intellij.plugin.telemetry.Telemetry
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.ui.AnimatedIcon
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
import java.util.*
import javax.swing.Icon
import kotlin.io.path.isDirectory

@Service
class CodacyCliService() {

    sealed interface CodacyCliState {
        val icon: Icon

        data object INSTALLED : CodacyCliState {
            override fun toString() = "Installed"
            override val icon: Icon = AllIcons.General.Information
        }

        data object INITIALIZED : CodacyCliState {
            override fun toString() = "Initialized"
            override val icon: Icon = IconUtils.CodacyIcon
        }

        data object INSTALLING : CodacyCliState {
            override fun toString() = "Installing"
            override val icon: Icon = AnimatedIcon.Default.INSTANCE
        }

        data object ANALYZING : CodacyCliState {
            override fun toString() = "Analyzing"
            override val icon: Icon = AnimatedIcon.Default.INSTANCE
        }

        data object ERROR : CodacyCliState {
            override fun toString() = "Error"
            override val icon: Icon = AllIcons.General.ErrorDialog
        }

        data object NOT_INSTALLED : CodacyCliState {
            override fun toString() = "Not Installed"
            override val icon: Icon = AllIcons.General.Gear
        }
    }

    lateinit var provider: Provider
    lateinit var organization: String
    lateinit var repository: String
    lateinit var project: Project
    lateinit var rootPath: String

    private lateinit var cliBehaviour: CodacyCliBehaviour
    private lateinit var pathsBehaviour: PathsBehaviour

    private var isServiceInstantiated: Boolean = false

    private val cliStateListeners = mutableListOf<() -> Unit>()

    private val config = Config.instance
    private var accountToken = config.storedApiToken
    private var serviceState = ServiceState.STARTING

    private val notificationManager = NotificationGroupManager
        .getInstance()
        .getNotificationGroup("CodacyNotifications")

    var codacyCliState: CodacyCliState = CodacyCliState.NOT_INSTALLED
    var cliCommand: String = ""

    private fun initService(
        provider: Provider,
        organization: String,
        repository: String,
        project: Project,
        cliBehaviour: CodacyCliBehaviour,
        pathsBehaviour: PathsBehaviour,
    ) {
        //TODO this logic might need to be changed to accommodate
        // provider/org/repo changing
        if (!isServiceInstantiated) {
            this.provider = provider
            this.organization = organization
            this.repository = repository
            this.project = project
            this.cliBehaviour = cliBehaviour
            this.pathsBehaviour = pathsBehaviour
            this.rootPath = pathsBehaviour.rootPath(project)

            setServiceState(ServiceState.RUNNING)

            isServiceInstantiated = true
        }


        val isSettingsPresent = isCodacySettingsPresent()
        val isCliShellFilePresent = isCliShellFilePresent()

        if (isSettingsPresent && isCliShellFilePresent) {
            updateWidgetState(CodacyCliState.INITIALIZED)
        } else if (isCliShellFilePresent) {
            updateWidgetState(CodacyCliState.INSTALLED)
        } else {
            updateWidgetState(CodacyCliState.NOT_INSTALLED)
        }
    }

    companion object {
        fun getService(project: Project): CodacyCliService {
            val gitProvider = GitProvider.getRepository(project)
                ?: throw IllegalStateException("No Git provider found for the project")

            val remote = gitProvider.remotes.firstOrNull()
                ?: throw IllegalStateException("No remote found in the Git repository")

            val gitInfo = GitRemoteParser.parseGitRemote(remote.firstUrl!!)

            return getService(
                Provider.fromString(gitInfo.provider),
                gitInfo.organization,
                gitInfo.repository,
                project,
            )
        }

        fun getService(
            provider: Provider,
            organization: String,
            repository: String,
            project: Project,
        ): CodacyCliService {
            val systemOs = System.getProperty("os.name").lowercase()
            val cli = project.getService(CodacyCliService::class.java)

            val (cliBehaviour, pathsBehaviour) = when {
                systemOs == "mac os x" || systemOs.contains("darwin") -> {
                    CliUnix() to PathsUnix()
                }

                systemOs == "linux" -> {
                    CliUnix() to PathsUnix()
                }

                systemOs.contains("windows") -> {
                    val process = ProcessBuilder("wsl", "--list", "--quiet")
                        .redirectErrorStream(true)
                        .start()

                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        val output = process.inputStream.bufferedReader().readText()
                        notificationGroup.createNotification(
                            "Windows Subsystem for Linux detection failure",
                            "WSL not present on this machine. Command output: $output",
                            NotificationType.WARNING
                        ).notify(project)
                    }

                    CliWindows() to PathsWindows()
                }

                else -> {
                    throw IllegalStateException("Unsupported OS: $systemOs")
                }
            }

            cli.initService(
                provider = provider,
                organization = organization,
                repository = repository,
                project = project,
                cliBehaviour = cliBehaviour,
                pathsBehaviour = pathsBehaviour
            )

            return cli
        }
    }

    suspend fun prepareCli(autoInstall: Boolean) {
        var _cliCommand = findCliCommand()

        if (!isCliShellFilePresent()) {
            updateWidgetState(CodacyCliState.INSTALLING)

            if (_cliCommand == null) {
                _cliCommand = installCli()
                if (_cliCommand == null) {
                    updateWidgetState(CodacyCliState.ERROR)
                    return
                }
            }

            updateWidgetState(CodacyCliState.INSTALLED)
            cliCommand = _cliCommand
        } else if (cliCommand.isBlank() && isCliShellFilePresent()) {
            updateWidgetState(CodacyCliState.INSTALLING)
            if (_cliCommand != null) {
                updateWidgetState(CodacyCliState.INSTALLED)
                cliCommand = _cliCommand
            } else {
                updateWidgetState(CodacyCliState.ERROR)

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
            updateWidgetState(CodacyCliState.INSTALLING)
            val initRes = initialize()
            if (initRes) {
                updateWidgetState(CodacyCliState.INITIALIZED)
            } else {
                updateWidgetState(CodacyCliState.ERROR)
            }
        } else if (isCodacySettingsPresent()) {
            updateWidgetState(CodacyCliState.INITIALIZED)
        }

    }

    fun installCli(): String? {
        val codacyConfigFullPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME)

        if (!isCodacyDirectoryPresent()) {
            codacyConfigFullPath.toFile().mkdirs()
        }

        val codacyCliPath =
            Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_SHELL_NAME).toAbsolutePath()

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
                Telemetry.track(CliInstallEvent)
                return pathsBehaviour.toCliPath(codacyCliPath.toString())
            }
        } else {
            return pathsBehaviour.toCliPath(codacyCliPath.toString())
        }
    }

    fun installDependencies(): Boolean {

        val program = cliBehaviour
            .buildCommand(pathsBehaviour.toCliPath(cliCommand), "install")
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
            pathsBehaviour.toCliPath(fullPath.toString())
        } else null
    }

    suspend fun initialize(): Boolean {
        val configFilePath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_YAML_NAME)
        val cliConfigFilePath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_CONFIG_NAME)
        val toolsFolderPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_TOOLS_CONFIGS_NAME)

        val initFilesOk = configFilePath.exists() && cliConfigFilePath.exists() && toolsFolderPath.exists()

        var needsInitialization = !initFilesOk

        if (initFilesOk) {
            val cliConfig =
                File(
                    Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_CONFIG_NAME).toString()
                ).readText()

            if ((cliConfig == "mode: local" && this.repository.isNotBlank()) || (cliConfig == "mode: remote" && this.repository.isBlank())) {
                needsInitialization = true
            }
        }

        if (needsInitialization) {
            val initParams = if (
                this.accountToken?.isNotBlank() == true &&
                this.repository.isNotBlank() &&
                this.organization.isNotBlank()
            ) {
                mapOf(
                    "provider" to this.provider.toString(),
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
        val process = cliBehaviour.buildCommand("curl", "-Ls", Config.CODACY_CLI_DOWNLOAD_LINK)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            return exitCode
        }

        val outputFile = Paths.get(pathsBehaviour.fromCliPath(outputPath))
        outputFile.toFile().writeText(output)

        return cliBehaviour.buildCommand("chmod", "+x", outputFile.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .start()
            .waitFor()
    }

    suspend fun analyze(file: String?, tool: String?): List<ProcessedSarifResult>? {
        prepareCli(true)

        updateWidgetState(CodacyCliState.ANALYZING)

        if (cliCommand.isBlank()) {
            updateWidgetState(CodacyCliState.INITIALIZED)

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

            withTempFile { _, tempFilePath ->

                val command = buildString {
                    append(cliCommand)
                    append(" analyze ")
                    append(" --output ")
                    append(pathsBehaviour.toCliPath(tempFilePath.toString()))
                    append(" ")
                    if (file != null) append(pathsBehaviour.toCliPath(file)).append(" ")
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
                        updateWidgetState(CodacyCliState.ERROR)
                    }
                }

                val fileOutput = runCatching {
                    File(tempFilePath.toUri()).readText()
                }

                if (fileOutput.isFailure) {
                    notificationManager.createNotification(
                        "CLI tools has not generated a result file",
                        "Current document will not be analyzed, please try again",
                        NotificationType.ERROR
                    ).notify(project)
                    return@withTempFile
                }

                results = fileOutput.getOrNull()
                    .let { SarifUtil.readReport(StringReader(it)).runs }
                    ?.let(::processSarifResults)
                    ?: emptyList()

                updateWidgetState(CodacyCliState.INITIALIZED)
            }

            return results
        } catch (error: Exception) {
            updateWidgetState(CodacyCliState.INITIALIZED)
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
    ): Result<Pair<String, String>> {

        return withContext(Dispatchers.IO) {
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
    }

    fun updateWidgetState(codacyCliState: CodacyCliState) {
        this.codacyCliState = codacyCliState
        cliStateListeners.forEach {
            it()
        }
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

    fun addStateListener(listener: () -> Unit) {
        cliStateListeners += listener
    }

    fun setServiceState(newServiceState: ServiceState) {
        if (serviceState != newServiceState) {
            serviceState = newServiceState
            project.messageBus
                .syncPublisher(WidgetStateListener.CLI_TOPIC)
                .stateChanged(serviceState)
        }
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
