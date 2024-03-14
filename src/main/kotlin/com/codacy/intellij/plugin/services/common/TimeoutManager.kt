package com.codacy.plugin.services.common

import kotlinx.coroutines.*

class TimeoutManager {
    private var timeoutJob: Job? = null

    fun startTimeout(timeoutMillis: Long, onTimeout: () -> Unit) {
        if (timeoutJob?.isActive == true) return

        timeoutJob = CoroutineScope(Dispatchers.Default).launch {
            delay(timeoutMillis)
            onTimeout()
        }
    }

    fun clearTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    fun isTimeoutRunning(): Boolean {
        return timeoutJob?.isActive == true
    }
}

fun main() = runBlocking {
    val manager = TimeoutManager()

    manager.startTimeout(1000L) {
        println("Timeout occurred!")
    }

    delay(500L)

    if (manager.isTimeoutRunning()) {
        println("Timeout is running. Clearing it now.")
        manager.clearTimeout()
    }

    delay(1100L)
    println("End of main")
}
