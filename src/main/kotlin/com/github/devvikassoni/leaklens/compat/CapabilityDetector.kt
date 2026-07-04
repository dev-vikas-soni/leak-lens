package com.github.devvikassoni.leaklens.compat

import com.intellij.openapi.project.Project

/**
 * Detects the presence of specific APIs in the current IDE instance.
 */
object CapabilityDetector {

    val hasAndroidPlugin: Boolean by lazy {
        ReflectionCache.getClass("org.jetbrains.android.sdk.AndroidSdkUtils") != null
    }

    /**
     * Checks if findAdb() is available (Modern AS Ladybug+)
     */
    val canUseFindAdb: Boolean by lazy {
        val clazz = ReflectionCache.getClass("org.jetbrains.android.sdk.AndroidSdkUtils")
        clazz?.let { ReflectionCache.getMethod(it, "findAdb", Project::class.java) != null }
            ?: false
    }

    /**
     * Checks if getDebugBridge() is available (Universal)
     */
    val canUseDebugBridge: Boolean by lazy {
        val clazz = ReflectionCache.getClass("org.jetbrains.android.sdk.AndroidSdkUtils")
        clazz?.let { ReflectionCache.getMethod(it, "getDebugBridge", Project::class.java) != null }
            ?: false
    }
}
