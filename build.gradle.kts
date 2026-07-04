import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog")
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
}

detekt {
    toolVersion = "1.23.6"
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(file("config/detekt/detekt.yml"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
    }
}

dependencies {
    // Shark - LeakCanary's heap analysis engine (pure JVM, no Android dependency)
    implementation("com.squareup.leakcanary:shark:2.14")
    implementation("com.squareup.leakcanary:shark-android:2.14")

    implementation("org.jetbrains.kotlin:kotlin-reflect:2.4.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")

    intellijPlatform {
        androidStudio("2024.2.2.13")
        testFramework(TestFrameworkType.Platform)
        bundledPlugins("org.jetbrains.android", "org.jetbrains.kotlin")
    }
}

intellijPlatform {
    buildSearchableOptions.set(false)

    pluginVerification {
        ides {
            recommended()
        }
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { localProperties.load(it) }
    }

    publishing {
        token.set(
            providers.environmentVariable("JB_MARKETPLACE_TOKEN")
                .orElse(providers.environmentVariable("PUBLISH_TOKEN"))
                .orElse(providers.gradleProperty("intellijPublishToken"))
                .orElse(providers.provider { localProperties.getProperty("intellijPublishToken") })
        )
        channels.set(
            listOf(
                providers.gradleProperty("intellijPublishChannels").getOrElse("default")
            )
        )
    }

    signing {
        certificateChain.set(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey.set(providers.environmentVariable("PRIVATE_KEY"))
        password.set(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }
}
