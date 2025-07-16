package com.codacy.intellij.plugin.services.common

import com.intellij.openapi.components.service
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*

@State(
    name = "codacy",
    storages = [Storage("CodacyPluginSettings.xml")]
)
@Service
class Config : PersistentStateComponent<Config.State> {

    //////////////// TODO, these are for CLI right now, prolly need to be moved to a different class
    var canInstallCli = false
    ////////////////
    private var state = State()
    private var apiToken: String? = null

    companion object {
        const val CLI_SHELL_NAME = "cli.sh"
        const val CODACY_FOLDER_NAME: String = ".codacy"
        const val CODACY_CLI_DOWNLOAD_LINK: String = "https://raw.githubusercontent.com/codacy/codacy-cli-v2/main/codacy-cli.sh"


        private val log = Logger

        val instance: Config
            get() = service()
    }

    data class State(
        var baseUri: String? = null,

        var availableCliVersions: List<String> = listOf(),
        var selectedCliVersion: String = ""
    )

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        this.apiToken = loadApiToken()
        log.info("Configuration loaded")
    }

    fun init() {
        apiToken = loadApiToken()
    }

    private fun loadApiToken(): String? {
        val credentialAttributes = createCredentialAttributes()
        val credentials = PasswordSafe.instance.get(credentialAttributes)
        return credentials?.getPasswordAsString()
    }

    fun storeApiToken(token: String?) {
        val credentialAttributes = createCredentialAttributes()
        if (token.isNullOrBlank()) {
            PasswordSafe.instance.set(credentialAttributes, null)
            apiToken = null
            log.info("API Token removed")
        } else {
            val credentials = Credentials("", token)
            PasswordSafe.instance.set(credentialAttributes, credentials)
            apiToken = token
            log.info("API Token stored")
        }
    }

    val cliVersion: String
        get() = state.selectedCliVersion //TODO better name

    val baseUri: String
        get() = state.baseUri ?: "https://app.codacy.com/api/v3"

    val loginUri: String = "https://app.codacy.com/auth/intellij"
//    val loginUri: String = "https://arielkosacoff.github.io/codacy-intellij-signin/"

    val storedApiToken: String?
        get() = apiToken

    private fun createCredentialAttributes(): CredentialAttributes =
        CredentialAttributes("Codacy", "")

}
