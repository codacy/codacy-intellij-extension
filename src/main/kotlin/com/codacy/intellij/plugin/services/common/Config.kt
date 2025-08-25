package com.codacy.intellij.plugin.services.common

import com.codacy.intellij.plugin.services.api.Api
import com.codacy.intellij.plugin.telemetry.Telemetry
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*
import com.intellij.util.SlowOperations
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

@State(
    name = "codacy",
    storages = [Storage("CodacyPluginSettings.xml")]
)
@Service
class Config : PersistentStateComponent<Config.State> {

    private var state = State()
    private var apiToken: String? = null

    companion object {
        const val CODACY_DIRECTORY_NAME: String = ".codacy"
        const val CODACY_CLI_SHELL_NAME = "cli.sh"
        const val CODACY_GITIGNORE_NAME: String = ".gitignore"
        const val CODACY_CLI_CONFIG_NAME: String = "cli-config.yaml"
        const val CODACY_YAML_NAME: String = "codacy.yaml"
        const val CODACY_LOGS_NAME: String = "logs"
        const val CODACY_TOOLS_CONFIGS_NAME: String = "tools-configs"

        const val CODACY_CLI_DOWNLOAD_LINK: String = "https://raw.githubusercontent.com/codacy/codacy-cli-v2/main/codacy-cli.sh"
        const val CODACY_CLI_RELEASES_LINK: String = "https://api.github.com/repos/codacy/codacy-cli-v2/releases"
        const val CODACY_CLI_V2_VERSION_ENV_NAME = "CODACY_CLI_V2_VERSION"


        private val log = Logger

        val instance: Config
            get() = service()
    }

    data class State(
        var baseUri: String? = null,
        var availableCliVersions: List<String> = listOf(),
        var selectedCliVersion: String = "",
        var hasSentInstallTelemetry: Boolean = false,
        var anonymousId: String = UUID.randomUUID().toString(),
        var userId: Int? = null,
        var isFirstRun: Boolean = true,
        var allowGenerateGuidelines: Boolean = false,
        var addAnalysisGuidelines: Boolean = false
    )

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        log.info("Configuration loaded")
    }

    fun init() {
        apiToken = loadApiToken()
    }

    private fun loadApiToken(): String? {
        val accessToken = SlowOperations.allowSlowOperations("Codacy: loadApiToken")
        return try {
            val credentialAttributes = createCredentialAttributes()
            val credentials = PasswordSafe.instance.get(credentialAttributes)
            credentials?.getPasswordAsString()
        } finally {
            accessToken.finish()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
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

            if (state.userId == null) {
                GlobalScope.launch {
                    val userProfile = Api().getUserProfile()

                    if (userProfile?.id != null && state.userId == null) {
                        state.userId = userProfile.id

                        Telemetry.identify()
                    }
                }
            }
        }
    }

    val cliVersion: String
        get() = state.selectedCliVersion

    val baseUri: String
        get() = state.baseUri ?: "https://app.codacy.com/api/v3"

    val loginUri: String = "https://app.codacy.com/auth/intellij"

    val storedApiToken: String?
        get() {
            return if(apiToken.isNullOrBlank()) {
                loadApiToken()
            } else apiToken
        }

    private fun createCredentialAttributes(): CredentialAttributes =
        CredentialAttributes("Codacy", "")
}
