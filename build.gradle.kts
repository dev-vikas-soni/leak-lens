import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
}

dependencies {
    // Shark - LeakCanary's heap analysis engine (pure JVM, no Android dependency)
    implementation("com.squareup.leakcanary:shark:2.14")
    implementation("com.squareup.leakcanary:shark-android:2.14")

    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        androidStudio("2024.2.2.13")
        testFramework(TestFrameworkType.Platform)
        bundledPlugin("org.jetbrains.android")
    }
}
