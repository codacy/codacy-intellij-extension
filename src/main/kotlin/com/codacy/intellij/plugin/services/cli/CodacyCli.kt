package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_V2_VERSION_ENV_NAME
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

abstract class CodacyCli(
    val provider: String,
    val organization: String,
    val repository: String,
    val project: Project
) {

//    var cliCommand: String = ""
//    var cliVersion: String = ""
    private val config = Config.instance

    companion object {
        private var codacyCli: CodacyCli? = null
        suspend fun getInstance(
            provider: String,
            organization: String,
            repository: String,
            project: Project
        ): CodacyCli {
            if (codacyCli == null) {
                codacyCli = createInstance(provider, organization, repository, project)
            } else if (
                codacyCli?.provider != provider ||
                codacyCli?.organization != organization ||
                codacyCli?.repository != repository
            ) {
                codacyCli = createInstance(provider, organization, repository, project)
            }
            return codacyCli!!
        }

        private suspend fun createInstance(
            provider: String,
            organization: String,
            repository: String,
            project: Project
        ): CodacyCli {
            val systemOs = System.getProperty("os.name").lowercase()

            val cli = when (systemOs) {
                "mac os x", "darwin" -> {
                    MacOsCli(provider, organization, repository, project)
                }

                "windows" -> {
                    //TODO
                    MacOsCli(provider, organization, repository, project)
                }

                else -> {
                    //TODO
                    MacOsCli(provider, organization, repository, project)
                }
            }
            cli.preflightCliCommand(true) //TODO originally false
            return cli
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

//            val a1 = cmd.split(" ").get(0)
//            val a2 = cmd.split(" ").slice(1 until cmd.split(" ").size)


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

    abstract suspend fun install()
}
