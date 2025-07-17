package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Config.Companion.CLI_SHELL_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_CONFIG_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_V2_VERSION_ENV_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_DIRECTORY_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_LOGS_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_TOOLS_CONFIGS_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_YAML_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.GITIGNORE_NAME
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

abstract class CodacyCli() {

    var cliCommand: String = ""

    lateinit var provider: String
    lateinit var organization: String
    lateinit var repository: String
    lateinit var project: Project

    var isServiceInstantiated: Boolean = false
        private set

    private val config = Config.instance

    fun initService(provider: String, organization: String, repository: String, project: Project) {
        //TODO need a way to force-recreate the instance
        if (!isServiceInstantiated) {
            this.provider = provider
            this.organization = organization
            this.repository = repository
            this.project = project
            isServiceInstantiated = true
        }
    }

    companion object {
        fun getService(
            provider: String,
            organization: String,
            repository: String,
            project: Project
        ): CodacyCli {
            val systemOs = System.getProperty("os.name").lowercase()

            val cli = when (systemOs) {
                "mac os x", "darwin" -> {
                    val cli = project.getService(MacOsCli::class.java)
                    cli.initService(provider, organization, repository, project)
                    cli
                }

                "windows" -> {
                    //TODO
                    val cli = project.getService(MacOsCli::class.java)
                    cli.initService(provider, organization, repository, project)
                    cli
                }

                else -> {
                    //TODO
                    val cli = project.getService(MacOsCli::class.java)
                    cli.initService(provider, organization, repository, project)
                    cli
                }
            }
            return cli
        }

        fun isCodacyDirectoryPresent(project: Project): Boolean {
            val codacyDir = Paths.get(project.basePath, CODACY_DIRECTORY_NAME)
            return codacyDir.exists() && codacyDir.isDirectory()
        }

        fun isCliShellFilePresent(project: Project): Boolean {
            val cliFile = Paths.get(project.basePath, CODACY_DIRECTORY_NAME, CLI_SHELL_NAME)
            return cliFile.exists() && cliFile.isFile()
        }

        fun isCodacySettingsPresent(project: Project): Boolean {
            val codacyDirectory = Paths.get(project.basePath, CODACY_DIRECTORY_NAME)

            val gitignoreFile = codacyDirectory.resolve(GITIGNORE_NAME)
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
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        // Stringify the args
        val argsString = args?.entries
            ?.joinToString(" ") { "--${it.key} ${it.value}" }
            ?: ""

        // Add the args to the command and remove any shell metacharacters
        val cmd = "$command $argsString".trim().replace(Regex("[;&|`$]"), "")
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification(
                "exec async cmd: $cmd",
                NotificationType.INFORMATION
            )
            .notify(project)

        //TODO improve error handling
        //TODO maybe just make it part of the Init if it doesn't change
        val rootPath = project.basePath ?: throw IllegalStateException("Project base path is not set")

        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification(
                    "cmd split ${cmd.split(" ")}",
                    NotificationType.INFORMATION
                )
                .notify(project)

            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification(
                    "directory $rootPath",
                    NotificationType.INFORMATION
                )
                .notify(project)


            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification(
                    "PRogram to execute: \n$cmd",
                    NotificationType.INFORMATION
                )
                .notify(project)

            val program = ProcessBuilder(cmd.split(" "))
                .directory(File(rootPath))
                .redirectErrorStream(false)


            //TODO better way to set environment variables
            program.environment()[CODACY_CLI_V2_VERSION_ENV_NAME] = config.cliVersion

            val process = program.start()


            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()

            process.waitFor()

            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification(
                    "process sdtOut: $stdout\nprocess stderr: $stderr",
                    NotificationType.INFORMATION
                )
                .notify(project)

            if (process.exitValue() != 0) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("CodacyNotifications")
                    .createNotification(
                        "shell failed",
                        NotificationType.WARNING
                    )
                    .notify(project)
                Result.failure(Exception(stderr.ifEmpty { "Unknown error" }))
            } else {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("CodacyNotifications")
                    .createNotification(
                        "shell successful",
                        NotificationType.WARNING
                    )
                    .notify(project)
                Result.success(Pair(stdout, stderr))
            }
        } catch (e: Exception) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification(
                    "EXCEPTION: ${e.message}",
                    NotificationType.WARNING
                )
                .notify(project)
            Result.failure(e)
        }
    }

//    fun isCodacyDirectoryPresent(): Boolean {
//        val codacyDir = Paths.get(project.basePath, CODACY_DIRECTORY_NAME)
//        return codacyDir.exists() && codacyDir.isDirectory()
//    }
//
//    fun isCliShellFilePresent(): Boolean {
//        val cliFile = Paths.get(project.basePath, CODACY_DIRECTORY_NAME, CLI_SHELL_NAME)
//        return cliFile.exists() && cliFile.isFile()
//    }
//
//    fun isCodacySettingsPresent(): Boolean {
//        val codacyDirectory = Paths.get(project.basePath, CODACY_DIRECTORY_NAME)
//
//        val gitignoreFile = codacyDirectory.resolve(GITIGNORE_NAME)
//        val cliConfigFile = codacyDirectory.resolve(CODACY_CLI_CONFIG_NAME)
//        val codacyYamlFile = codacyDirectory.resolve(CODACY_YAML_NAME)
//        val logsDirectory = codacyDirectory.resolve(CODACY_LOGS_NAME)
//        val toolsConfigsDirectory = codacyDirectory.resolve(CODACY_TOOLS_CONFIGS_NAME)
//
//        return (gitignoreFile.exists() && gitignoreFile.isFile() &&
//                cliConfigFile.exists() && cliConfigFile.isFile() &&
//                codacyYamlFile.exists() && codacyYamlFile.isFile() &&
//                logsDirectory.exists() && logsDirectory.isDirectory() &&
//                toolsConfigsDirectory.exists() && toolsConfigsDirectory.isDirectory())
//    }

    abstract suspend fun prepareCli(autoInstall: Boolean = false)

    abstract suspend fun installCli(): String?
}
