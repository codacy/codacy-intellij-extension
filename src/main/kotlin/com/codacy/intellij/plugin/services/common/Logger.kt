package com.codacy.plugin.services.common

import com.intellij.openapi.diagnostic.Logger as IntelliJLogger

class Logger {

    companion object {
        private val log = IntelliJLogger.getInstance(Logger::class.java)

//        fun trace(message: String, component: String? = null) {
//            log.debug(logString("TRACE: $message", component))
//        }
//
//        fun debug(message: String, component: String? = null) {
//            log.debug(logString("DEBUG: $message", component))
//        }

        fun info(message: String, component: String? = null) {
            log.info(logString(message, component))
        }

        fun warn(message: String, component: String? = null) {
            log.warn(logString(message, component))
        }

        fun error(message: String, component: String? = null) {
            log.error(logString(message, component))
        }

        private fun logString(message: String, component: String?): String {
            return component?.let { "$component> $message" } ?: message
        }
    }
}
