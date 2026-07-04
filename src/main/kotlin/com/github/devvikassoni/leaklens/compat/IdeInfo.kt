package com.github.devvikassoni.leaklens.compat

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.BuildNumber

/**
 * Information about the current IDE instance.
 */
object IdeInfo {
    val build: BuildNumber
        get() = ApplicationInfo.getInstance().build

    val isAndroidStudio: Boolean
        get() {
            val name = ApplicationInfo.getInstance().fullApplicationName
            return name.contains("Android Studio") || name.contains("AndroidStudio")
        }

    val fullVersion: String
        get() = ApplicationInfo.getInstance().fullVersion

    override fun toString(): String {
        return "IDE Info: $fullVersion ($build), Android Studio: $isAndroidStudio"
    }
}
