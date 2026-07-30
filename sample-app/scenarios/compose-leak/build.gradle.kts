plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.github.devvikassoni.leaklens.sample.scenarios.compose"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.runtime:runtime:1.6.8")
}
