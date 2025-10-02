package com.codacy.intellij.plugin.telemetry

import com.codacy.intellij.plugin.services.common.Config
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.segment.analytics.kotlin.core.Analytics

sealed class TelemetryEvent(open val eventKey: String) {
    abstract fun toPayload(): Map<String, Any?>
}

data object ExtensionInstalledEvent : TelemetryEvent("extension_installed") {
    override fun toPayload(): Map<String, Any?> = mapOf()
}

data object CliInstallEvent : TelemetryEvent("cli_install") {
    override fun toPayload(): Map<String, Any?> = emptyMap()
}

data object ExtensionUnloadedEvent : TelemetryEvent("extension_unloaded") {
    override fun toPayload(): Map<String, Any?> = emptyMap()
}

data class RepositoryStateChangeEvent(val state: String) : TelemetryEvent("Repository State Change") {
    override fun toPayload(): Map<String, Any?> = mapOf("state" to state)
}

data class BranchStateChangeEvent(val state: String) : TelemetryEvent("Branch State Change") {
    override fun toPayload(): Map<String, Any?> = mapOf("state" to state)
}

data class PullRequestStateChangeEvent(val state: String) : TelemetryEvent("Pull Request State Change") {
    override fun toPayload(): Map<String, Any?> = mapOf("state" to state)
}

data class UnexpectedErrorEvent(val message: String) : TelemetryEvent("Unexpected Error") {
    override fun toPayload(): Map<String, Any?> =
        mapOf("message" to message)
}

@Service
object Telemetry {

    private fun normalizedIde(): String {
        val product = ApplicationNamesInfo.getInstance().productName.lowercase()
        val productKey = when {
            product.contains("intellij") -> "intellij"
            product.contains("webstorm") -> "webstorm"
            product.contains("pycharm") -> "pycharm"
            product.contains("phpstorm") -> "phpstorm"
            product.contains("rubymine") -> "rubymine"
            product.contains("goland") -> "goland"
            product.contains("rider") -> "rider"
            product.contains("clion") -> "clion"
            product.contains("datagrip") -> "datagrip"
            product.contains("dataspell") -> "dataspell"
            else -> product.split(" ").firstOrNull() ?: "unknown"
        }
        return "jetbrains $productKey"
    }

    private fun normalizedOs(): String {
        val raw = (System.getProperty("os.name") ?: "unknown").lowercase()
        return when {
            raw.contains("mac") || raw.contains("darwin") -> "darwin"
            raw.contains("win") -> "win32"
            raw.contains("nux") || raw.contains("nix") || raw.contains("aix") || raw.contains("linux") -> "linux"
            else -> raw
        }
    }

    private val ide: String by lazy { normalizedIde() }
    private val os: String by lazy { normalizedOs() }

    // Organization context intentionally omitted to keep PR concise

    private val analytics: Analytics by lazy {
        Analytics("ckhmOOSC1drlNKLYmzbK6BAJo8drHqNQ") {
            application = "Codacy Intellij Plugin"
            flushAt = 3
            flushInterval = 10
        }
    }

    fun identify() {
        val config = Config.instance
        val userId = config.state.userId
        val anonymousId = config.state.anonymousId

        analytics.identify(
            userId = userId.toString(),
            traits = mapOf(
                "anonymousId" to anonymousId,
                "ide" to ide,
                "os" to os
            )
        )
    }

    fun track(event: TelemetryEvent) {
        val config = Config.instance
        val userId = config.state.userId
        val anonymousId = config.state.anonymousId

        analytics.track(
            name = event.eventKey,
            properties = mapOf(
                "anonymousId" to anonymousId,
                "userId" to userId,
                "ide" to ide,
                "os" to os
            ) + event.toPayload()
        )
    }
}
