package com.codacy.intellij.plugin.listeners

import com.codacy.intellij.plugin.services.cli.CodacyCli
import com.intellij.util.messages.Topic

enum class ServiceState { IDLE, RUNNING, ERROR }

interface WidgetStateListener {
    companion object {
        val TOPIC = Topic.create("ServiceStateChanged", WidgetStateListener::class.java)
    }

    fun stateChanged(newState: ServiceState)
}
