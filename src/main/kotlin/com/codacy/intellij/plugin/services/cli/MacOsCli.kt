package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.common.Config
import com.codacy.intellij.plugin.services.common.Config.Companion.CLI_SHELL_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_DOWNLOAD_LINK
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_CLI_V2_VERSION_ENV_NAME
import com.codacy.intellij.plugin.services.common.Config.Companion.CODACY_FOLDER_NAME
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Paths

class MacOsCli(provider: String, organization: String, repository: String, project: Project) :
    CodacyCli(provider, organization, repository, project) {

    private val config = Config.instance

    var accountToken = config.storedApiToken

    //TODO ✅
    suspend fun findCliCommand(project: Project, autoInstall: Boolean = false) {

        val localPath = Paths.get(CODACY_FOLDER_NAME, CLI_SHELL_NAME)
        //TODO make sure toAboslutePath makes sense here
        val fullPath = Paths.get(project.basePath, CODACY_FOLDER_NAME, CLI_SHELL_NAME).toAbsolutePath()

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
        if(fullPath.exists()) {
            config.state.cliCommand = localPath.toString()
            if(config.cliVersion.isNotBlank()) {
                //TODO i might not need this anymore
//                cliVersion = "$CODACY_CLI_V2_VERSION_ENV_NAME=${config.cliVersion}"
            }
        }


        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification("fullPath: " + fullPath, NotificationType.INFORMATION)
            .notify(project)

        if (autoInstall) {
            install()
        }
    }

    suspend fun preflightCliCommand(autoInstall: Boolean) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification("preflight", NotificationType.INFORMATION)
            .notify(project)

        if (config.state.cliCommand.isBlank()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification("cli is blank", NotificationType.INFORMATION)
                .notify(project)

            findCliCommand(project, autoInstall)
        } else {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("CodacyNotifications")
                .createNotification("init", NotificationType.INFORMATION)
                .notify(project)

            initialize() //TODO//async
        }
    }

    override suspend fun install() {
        //TODO Set some setting that cli is installing?
//        await vscode.commands.executeCommand('setContext', 'codacy:cliInstalling', true)

        val prj = project

        ProgressManager.getInstance().run(object : Task.Backgroundable(null, "Installing CLI", false) {
            override fun run(progressIndicator: ProgressIndicator) {
                progressIndicator.isIndeterminate = true

                val fullPath = Paths.get(prj?.basePath, CODACY_FOLDER_NAME)

                //TODO some progress window like in vscode?
                if (!fullPath.exists()) {
                    fullPath.toFile().mkdirs()
                }

                val codacyCliPath = Paths.get(prj?.basePath, CODACY_FOLDER_NAME, CLI_SHELL_NAME)

                if (!codacyCliPath.exists()) {
                    // Download cli.sh if it doesn't exist

                    // const execPath = this.preparePathForExec(codacyCliPath)
                    //TODO some preflight check for curl?
                    val execOutputPath = codacyCliPath.toAbsolutePath().toString()

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
                    config.state.cliCommand = execOutputPath
//                    cliVersion = config.cliVersion

                    //TODO should really not block it
                    runBlocking {
                        initialize()
                    }
                }
            }
        })

    }

    fun installDependencies() {

//        val a2 = cliCommand.split(" ").slice(1 until cliCommand.split(" ").size)
//            .joinToString(" ")

        NotificationGroupManager.getInstance()
            .getNotificationGroup("CodacyNotifications")
            .createNotification("a2: ${config.state.cliCommand}", NotificationType.INFORMATION)
            .notify(project)

        val program = ProcessBuilder(config.state.cliCommand, "install")
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

    fun update() {

    }

    suspend fun initialize() {
// Check if the configuration files exist
        //TODO hardcode file names

        //TODO better exception handling
        val rootPath = project.basePath ?: throw RuntimeException("Project base path is not set")


        val configFilePath = Paths.get(rootPath, CODACY_FOLDER_NAME, "codacy.yaml")
        val cliConfigFilePath = Paths.get(rootPath, CODACY_FOLDER_NAME, "cli-config.yaml")
        val toolsFolderPath = Paths.get(rootPath, CODACY_FOLDER_NAME, "tools-configs")

        val initFilesOk = configFilePath.exists() && cliConfigFilePath.exists() && toolsFolderPath.exists()

        var needsInitialization = !initFilesOk

        if (initFilesOk) {
            // Check if the mode matches the current properties

            //TODO make sure path is okay, maybe needs to be absolute
            val cliConfig =
                File(Paths.get(rootPath, CODACY_FOLDER_NAME, "cli-config.yaml").toString()).readText(/*UTF-8*/)

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
                execAsync("${config.state.cliCommand} init", initParams) //TODO CLI command to be written
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

    fun analyze() {

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
