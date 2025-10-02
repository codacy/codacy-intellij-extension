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

// Branch state change telemetry removed (no longer tracked)

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
        val known = listOf(
            "intellij",
            "webstorm",
            "pycharm",
            "phpstorm",
            "rubymine",
            "goland",
            "rider",
            "clion",
            "datagrip",
            "dataspell"
        )

        val match = known.firstOrNull { product.contains(it) }
        val productKey = match ?: product.split(" ").firstOrNull() ?: "unknown"
        return "jetbrains $productKey"
    }

    private fun normalizedOs(): String {
        val raw = (System.getProperty("os.name") ?: "unknown").lowercase()
        val isMac = raw.contains("mac") || raw.contains("darwin")
        val isWin = raw.contains("win")
        val linuxTokens = listOf("nux", "nix", "aix", "linux")
        val isLinux = linuxTokens.any { raw.contains(it) }

        return when {
            isMac -> "darwin"
            isWin -> "win32"
            isLinux -> "linux"
            else -> raw
        }
    }

    private val ide: String by lazy { normalizedIde() }
    private val os: String by lazy { normalizedOs() }

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
