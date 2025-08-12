package com.codacy.intellij.plugin.listeners

import com.intellij.util.messages.Topic

enum class ServiceState { STARTING, RUNNING, ERROR }

interface WidgetStateListener {
    companion object {
        val CLI_TOPIC = Topic.create("CLIStateChange", WidgetStateListener::class.java)
        val AI_AGENT_TOPIC = Topic.create("AiAgentStateChange", WidgetStateListener::class.java)
    }

    fun stateChanged(newState: ServiceState)
}
