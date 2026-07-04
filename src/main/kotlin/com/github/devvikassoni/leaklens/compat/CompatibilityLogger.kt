package com.github.devvikassoni.leaklens.compat

import com.intellij.openapi.diagnostic.thisLogger

/**
 * Centralized logger for compatibility-related events and decisions.
 */
object CompatibilityLogger {
    private val logger = thisLogger()

    fun info(message: String) {
        logger.info("[Compat] $message")
    }

    fun debug(message: String) {
        logger.debug("[Compat] $message")
    }

    fun warn(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger.warn("[Compat] $message", throwable)
        } else {
            logger.warn("[Compat] $message")
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger.error("[Compat] $message", throwable)
        } else {
            logger.error("[Compat] $message")
        }
    }
}
