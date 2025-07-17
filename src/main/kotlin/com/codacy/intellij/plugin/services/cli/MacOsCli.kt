package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Config.Companion.CLI_SHELL_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_DOWNLOAD_LINK
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_V2_VERSION_ENV_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_DIRECTORY_NAME
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Paths

@Service
class MacOsCli() : CodacyCli() {

    private val config = Config.instance

    var accountToken = config.storedApiToken

    //TODO ✅
    suspend fun findCliCommand(project: Project): String? {

        //TODO I dont think I actually need this
        val localPath = Paths.get(CODACY_DIRECTORY_NAME, CLI_SHELL_NAME)
        //TODO make sure toAboslutePath makes sense here
        val fullPath = Paths.get(project.basePath, CODACY_DIRECTORY_NAME, CLI_SHELL_NAME).toAbsolutePath()

        // check if .codacy/cli.sh exists
//        if (fullPath.exists()) {
//            cliCommand = if (config.cliVersion.isBlank()) {
//                localPath.toString()
//            } else {
//                "CODACY_CLI_V2_VERSION=${config.cliVersion} $localPath"
//            }
//            return
//        }

        //TODO new approach, env will be separate
        // still not exactly happy about it
        if (isCliShellFilePresent(project)) {
//            cliCommand = localPath.toString()
            return fullPath.toString()
        } else return null


//        NotificationGroupManager.getInstance()
//            .getNotificationGroup("CodacyNotifications")
//            .createNotification("fullPath: " + fullPath, NotificationType.INFORMATION)
//            .notify(project)
//
//        if (autoInstall) {
//            install()
//        }
    }

    //TODO I think this should be a more general function to install the entire thing
    override suspend fun prepareCli(autoInstall: Boolean) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification("preflight", NotificationType.INFORMATION)
            .notify(project)

        if (cliCommand.isBlank()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification("cli is blank", NotificationType.INFORMATION)
                .notify(project)

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

        if (autoInstall) {
            //TODO better name
            val isInitalized = isCodacySettingsPresent(project)

            if (!isInitalized) {
                initialize()
            }
        }


//        else {
//            NotificationGroupManager.getInstance()
//                .getNotificationGroup("CodacyNotifications")
//                .createNotification("init", NotificationType.INFORMATION)
//                .notify(project)
//
//            initialize() //TODO//async
//        }
    }

    override suspend fun installCli(): String? {
        //TODO Set some setting that cli is installing?
//        await vscode.commands.executeCommand('setContext', 'codacy:cliInstalling', true)

        val prj = project


        val fullPath = Paths.get(prj?.basePath, CODACY_DIRECTORY_NAME)

        //TODO some progress window like in vscode?
        if (!isCodacyDirectoryPresent(project)) {
            fullPath.toFile().mkdirs()
        }

        val codacyCliPath = Paths.get(prj?.basePath, CODACY_DIRECTORY_NAME, CLI_SHELL_NAME)

        if (!codacyCliPath.exists()) {
            // Download cli.sh if it doesn't exist

            // const execPath = this.preparePathForExec(codacyCliPath)
            //TODO some preflight check for curl?
            val execOutputPath = codacyCliPath.toAbsolutePath().toString()

            //TODO add a check if it downloaded successfully
            runBlocking {//TODO make sure this will be okay
                downloadCodacyCli(execOutputPath, prj)
            }
            // const execPath = this.preparePathForExec(codacyCliPath) //TODO this is not used for UNIX but for windows to normialize path

            //TODO old approach
//                    cliCommand = if (config.cliVersion.isBlank()) {
//                        execOutputPath
//                    } else {
//                        "CODACY_CLI_V2_VERSION=${config.cliVersion} $execOutputPath"
//                    }

            //TODO new approach, env will be separate
            // still not exactly happy about it
            //TODO make sure this makes sense
//            cliCommand = execOutputPath
//                    cliVersion = config.cliVersion

            return execOutputPath
            //TODO should really not block it
//            runBlocking {
//                initialize()
//            }
        }

        return null
    }

    fun installDependencies() {

//        val a2 = cliCommand.split(" ").slice(1 until cliCommand.split(" ").size)
//            .joinToString(" ")

        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification("a2: ${cliCommand}", NotificationType.INFORMATION)
            .notify(project)

        val program = ProcessBuilder(cliCommand, "install")
            .redirectErrorStream(true)

        //TODO work on hardcoded value
        program.environment()[CODACY_CLI_V2_VERSION_ENV_NAME] = config.cliVersion

        val process = program.start()

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Curl failed with exit code $exitCode")
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification("Dependencies installed successfully", NotificationType.INFORMATION)
            .notify(project)
    }

    suspend fun initialize() {
// Check if the configuration files exist
        //TODO hardcode file names

        //TODO better exception handling
        val rootPath = project.basePath ?: throw RuntimeException("Project base path is not set")


        val configFilePath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, "codacy.yaml")
        val cliConfigFilePath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, "cli-config.yaml")
        val toolsFolderPath = Paths.get(rootPath, CODACY_DIRECTORY_NAME, "tools-configs")

        val initFilesOk = configFilePath.exists() && cliConfigFilePath.exists() && toolsFolderPath.exists()

        var needsInitialization = !initFilesOk

        if (initFilesOk) {
            // Check if the mode matches the current properties

            //TODO make sure path is okay, maybe needs to be absolute
            val cliConfig =
                File(Paths.get(rootPath, CODACY_DIRECTORY_NAME, "cli-config.yaml").toString()).readText(/*UTF-8*/)

            if ((cliConfig == "mode: local" && this.repository.isNotBlank()) || (cliConfig == "mode: remote" && this.repository.isBlank())) {
                needsInitialization = true
            }
        }


        if (needsInitialization) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification("needsInit", NotificationType.INFORMATION)
                .notify(project)


            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification(
                    "whats the api token: ${this.accountToken}",
                    NotificationType.INFORMATION
                )
                .notify(project)

            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification(
                    "this.accountToken?.isNotBlank() == true = ${this.accountToken?.isNotBlank() == true}" +
                            "\nthis.repository.isNotBlank() = ${this.repository.isNotBlank()}" +
                            "\nthis.provider.isNotBlank() = ${this.provider.isNotBlank()}" +
                            "\nthis.organization.isNotBlank() = ${this.organization.isNotBlank()}",
                    NotificationType.INFORMATION
                )
                .notify(project)


            val initParams = if (
                this.accountToken?.isNotBlank() == true && //TODO check this logic
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
                // initialize codacy-cli
                //TODO make sure this is correct
                execAsync("${cliCommand} init", initParams) //TODO CLI command to be written
            } catch (error: Exception) {

                NotificationGroupManager.getInstance()
                    .getNotificationGroup("CodacyNotifications")
                    .createNotification(
                        "error when trying to execAsync, error: $error",
                        NotificationType.ERROR
                    )
                    .notify(project)
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

    suspend fun downloadCodacyCli(outputPath: String, project: Project) {
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

        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification("Successfully downloaded script", NotificationType.INFORMATION)
            .notify(project)
    }


}
