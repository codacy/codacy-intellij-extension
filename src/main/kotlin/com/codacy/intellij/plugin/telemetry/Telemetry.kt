package com.codacy.intellij.plugin.telemetry

import com.intellij.openapi.diagnostic.Logger
import com.codacy.intellij.plugin.services.common.Config
import com.intellij.openapi.components.Service
import com.segment.analytics.kotlin.core.Analytics
import kotlinx.coroutines.runBlocking

// Base sealed class for all telemetry events
sealed class TelemetryEvent(open val eventKey: String) {
    abstract fun toPayload(): Map<String, Any?>
}

// Example: Extension Installed Event
data class ExtensionInstalledEvent(
    val os: String
) : TelemetryEvent("extension_installed") {
    override fun toPayload(): Map<String, Any?> = mapOf(
        "os" to os
    )
}

// Example: Unexpected Error Event
data class UnexpectedErrorEvent(
    val message: String,
    val component: String
) : TelemetryEvent("Unexpected Error") {
    override fun toPayload(): Map<String, Any?> = mapOf(
        "message" to message,
        "component" to component
    )
}

// Extension Deactivated Event
object ExtensionDeactivatedEvent : TelemetryEvent("extension_deactivated") {
    override fun toPayload(): Map<String, Any?> = emptyMap()
}

// Extension Uninstalled Event
object ExtensionUninstalledEvent : TelemetryEvent("extension_uninstalled") {
    override fun toPayload(): Map<String, Any?> = emptyMap()
}

// Repository State Change Event
data class RepositoryStateChangeEvent(
    val state: String,
    val repo: String? = null
) : TelemetryEvent("Repository State Change") {
    override fun toPayload(): Map<String, Any?> = mapOf(
        "state" to state,
        "repo" to repo
    ).filterValues { it != null }
}

// Pull Request State Change Event
data class PullRequestStateChangeEvent(
    val state: String,
    val prId: String? = null
) : TelemetryEvent("Pull Request State Change") {
    override fun toPayload(): Map<String, Any?> = mapOf(
        "state" to state,
        "prId" to prId
    ).filterValues { it != null }
}

// Branch State Change Event
data class BranchStateChangeEvent(
    val state: String,
    val branch: String? = null
) : TelemetryEvent("Branch State Change") {
    override fun toPayload(): Map<String, Any?> = mapOf(
        "state" to state,
        "branch" to branch
    ).filterValues { it != null }
}

// CLI Install Event
object CliInstallEvent : TelemetryEvent("cli_install") {
    override fun toPayload(): Map<String, Any?> = emptyMap()
}

object SegmentAnalyticsHolder {
    val analytics: Analytics by lazy {
        Analytics("9jEJR2TvwHp7BP1M0TicdZbhVmUG90GC") {
            flushAt = 3
            flushInterval = 10
        }
    }
}

@Service
object Telemetry {

    const val IDE: String = "jetbrains"

    fun identify() {
        val config = Config.instance
        val userId = config.state.userId
        val anonymousId = config.state.anonymousId
        val os = System.getProperty("os.name") ?: "unknown"

        runBlocking {
            SegmentAnalyticsHolder.analytics.identify(
                userId = userId.toString(),
                traits = mapOf(
                    "ide" to IDE,
                    "os" to os,
                    "anonymousId" to anonymousId
                )
            )
        }
    }

    fun track(event: TelemetryEvent, extraProperties: Map<String, Any?> = emptyMap()) {
        val config = Config.instance
        val userId = config.state.userId
        val anonymousId = config.state.anonymousId
        val os = System.getProperty("os.name") ?: "unknown"

        val properties = mutableMapOf<String, Any?>(
            "ide" to IDE,
            "os" to os
        )

        properties.putAll(event.toPayload())
        properties.putAll(extraProperties)

        SegmentAnalyticsHolder.analytics.track(
            name = event.eventKey,
            properties = mapOf(
                "anonymousId" to anonymousId,
                "userId" to userId
            ) + properties
        )
    }
}
