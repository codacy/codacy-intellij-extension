package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_CONFIG_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_SHELL_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_V2_VERSION_ENV_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_DIRECTORY_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_GITIGNORE_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_LOGS_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_TOOLS_CONFIGS_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_YAML_NAME
import com.codacy.intellij.plugin.views.CodacyCliToolWindowFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.util.io.isFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import com.jetbrains.qodana.sarif.SarifUtil

abstract class CodacyCli() {

    var cliCommand: String = ""

    lateinit var provider: String
    lateinit var organization: String
    lateinit var repository: String
    lateinit var project: Project
    lateinit var rootPath: String
    lateinit var cliWindowFactory: CodacyCliToolWindowFactory

    var isServiceInstantiated: Boolean = false
        private set

    private val config = Config.instance
    protected val notificationManager = NotificationGroupManager
        .getInstance()
        .getNotificationGroup("CodacyNotifications")

    fun initService(
        provider: String,
        organization: String,
        repository: String,
        project: Project,
        cliWindowFactory: CodacyCliToolWindowFactory
    ) {
        val rootPath = project.basePath
            ?: throw IllegalStateException("Project base path is not set")

        if (!isServiceInstantiated) {
            this.provider = provider
            this.organization = organization
            this.repository = repository
            this.project = project
            this.rootPath = rootPath
            this.cliWindowFactory = cliWindowFactory
            isServiceInstantiated = true
        }
    }

    companion object {
        fun getService(
            provider: String,
            organization: String,
            repository: String,
            project: Project,
            cliWindowFactory: CodacyCliToolWindowFactory
        ): CodacyCli {
            val systemOs = System.getProperty("os.name").lowercase()

            val cli = when (systemOs) {
                "mac os x", "darwin" -> {
                    val cli = project.getService(MacOsCli::class.java)
                    cli.initService(provider, organization, repository, project, cliWindowFactory)
                    cli
                }

                "windows" -> {
                    //TODO
                    val cli = project.getService(MacOsCli::class.java)
                    cli.initService(provider, organization, repository, project, cliWindowFactory)
                    cli
                }

                else -> {
                    //TODO
                    val cli = project.getService(MacOsCli::class.java)
                    cli.initService(provider, organization, repository, project, cliWindowFactory)
                    cli
                }
            }

            cliWindowFactory.updateCliStatus(
                isCliShellFilePresent(project),
                isCodacySettingsPresent(project)
            )
            return cli
        }

        fun isCodacyDirectoryPresent(project: Project): Boolean {
            val rootPath = project.basePath ?: return false
            val codacyDir = Paths.get(rootPath, CODACY_DIRECTORY_NAME)
            return codacyDir.exists() && codacyDir.isDirectory()
        }

        fun isCliShellFilePresent(project: Project): Boolean {
            val rootPath = project.basePath ?: return false
            val cliFile = Paths.get(rootPath, CODACY_DIRECTORY_NAME, CODACY_CLI_SHELL_NAME)
            return cliFile.exists() && cliFile.isFile()
        }

        fun isCodacySettingsPresent(project: Project): Boolean {
            val rootPath = project.basePath ?: return false
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

    suspend fun execAsync(
        command: String,
        args: Map<String, String>? = null
    ): kotlin.Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        // Stringify the args
        val argsString = args?.entries
            ?.joinToString(" ") { "--${it.key} ${it.value}" }
            ?: ""

        // Add the args to the command and remove any shell metacharacters
        val cmd = "$command $argsString".trim().replace(Regex("[;&|`$]"), "")

        try {

            notificationManager.createNotification("test command: ", "$cmd", NotificationType.INFORMATION)
                .notify(project)


            notificationManager.createNotification("test rootPath: ", "$rootPath", NotificationType.INFORMATION)
                .notify(project)

            val program = ProcessBuilder(cmd.split(" "))
                .directory(File(rootPath))
                .redirectErrorStream(false)
            program.environment()[CODACY_CLI_V2_VERSION_ENV_NAME] = config.cliVersion

            val process = program.start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()

            process.waitFor()

            if (process.exitValue() != 0) {
                kotlin.Result.failure(Exception(stderr.ifEmpty { "Unknown error" }))
            } else {
                kotlin.Result.success(Pair(stdout, stderr))
            }
        } catch (e: Exception) {
            kotlin.Result.failure(e)
        }
    }

    //TODO in vscode, tools param is currently not used
    abstract suspend fun analyze(file: String?, tool: String? = null): List<ProcessedSarifResult>?

    abstract suspend fun prepareCli(autoInstall: Boolean = false)

    abstract suspend fun installCli(): String?


}
