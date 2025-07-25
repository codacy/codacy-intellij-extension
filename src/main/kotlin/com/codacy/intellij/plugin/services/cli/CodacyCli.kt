package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.cli.models.ProcessedSarifResult
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
import com.codacy.intellij.plugin.services.common.PrepareCommand
import com.codacy.intellij.plugin.services.git.GitProvider
import com.codacy.intellij.plugin.views.CodacyCliStatusBarWidget
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.util.io.isFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

abstract class CodacyCli() {

    //In the vscode extension, tools param is currently not used
    abstract suspend fun analyze(file: String?, tool: String? = null): List<ProcessedSarifResult>?

    abstract suspend fun prepareCli(autoInstall: Boolean = false)

    abstract suspend fun installCli(): String?

    var cliCommand: String = ""

    lateinit var provider: String
    lateinit var organization: String
    lateinit var repository: String
    lateinit var project: Project
    lateinit var rootPath: String

    var isServiceInstantiated: Boolean = false

    protected var codacyStatusBarWidget: CodacyCliStatusBarWidget? = null

    private val config = Config.instance
    protected val notificationManager = NotificationGroupManager
        .getInstance()
        .getNotificationGroup("CodacyNotifications")

    open fun initService(
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

            val cli = when (systemOs) {
                "mac os x", "darwin" -> {
                    val cli = project.getService(MacOsCli::class.java)
                    cli
                }

                "windows" -> {
                    try {
                        val process = PrepareCommand("wsl", "--status").start()

                        process.waitFor()

                        val isWSLSupported =
                            process.inputStream.bufferedReader().readText().contains("Default Distribution")

                        if (isWSLSupported) {
                            project.getService(WinWSLCodacyCli::class.java)
                        } else {
                            project.getService(WinCodacyCli::class.java)
                        }
                    } catch (e: Exception) {
                        notificationGroup.createNotification(
                            "Window Subsystem for Linux detection failure",
                            "Reverting to unsupported non-WSL mode. Process failed with error: ${e.message}",
                            NotificationType.WARNING
                        )

                        project.getService(WinCodacyCli::class.java)
                    }
                }

                else -> {
                    //TODO
                    val cli = project.getService(MacOsCli::class.java)
                    cli.initService(provider, organization, repository, project)
                    cli
                }
            }
            cli.initService(provider, organization, repository, project)

            return cli
        }
    }

    open suspend fun execAsync(
        command: String,
        args: Map<String, String>? = null
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val commandList = buildList {
            addAll(command.split(" ").filter { it.isNotBlank() })
            args?.forEach { (k, v) ->
                add("--$k")
                add(v)
            }
        }

        try {
            val program = ProcessBuilder(commandList)
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

}
