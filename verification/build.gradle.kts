import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.repositories

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("application")
}

group = "com.github.devvikassoni.leaklens.verification"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(project(":")) // Link to the main plugin engine
    implementation("com.squareup.leakcanary:shark:2.14")
    implementation("com.squareup.leakcanary:shark-android:2.14")
    implementation("com.google.code.gson:gson:2.14.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.github.devvikassoni.leaklens.verification.VerificationRunnerKt")
}

tasks.register<JavaExec>("verify") {
    group = "verification"
    description = "Verifies current engine output against goldens"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.github.devvikassoni.leaklens.verification.VerificationRunnerKt")

    val scenario = project.findProperty("scenario") as? String
    if (scenario != null) {
        args("--scenario", scenario)
    }
}
