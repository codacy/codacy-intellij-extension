package com.codacy.intellij.plugin.services.cli

import com.codacy.intellij.plugin.services.common.Config

interface CodacyCli {

    val MAX_BUFFER_SIZE: Int
        get() = 1024 * 1024 * 10

    val codacyDirectoryName: String
        get() = ".codacy"

    val rootPath: String
    val provider: String
    val organization: String
    val repository: String


    fun install(): Unit

    fun installDependencies(): Unit

    fun update(): Unit

    fun getCliCommands(): Unit

    fun analyze(): Unit

    val _accountToken: String?
        get() = Config.instance.storedApiToken


    val _cliVersion: String?
        get() = Config.CodacyCli().cliVersion

}
