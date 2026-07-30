plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.github.devvikassoni.leaklens.sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.github.devvikassoni.leaklens.sample"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":scenarios:activity-leak"))
    implementation(project(":scenarios:compose-leak"))
    implementation(project(":scenarios:flow-leak"))
    implementation(project(":scenarios:fragment-leak"))
    implementation(project(":scenarios:singleton-leak"))
    implementation(project(":scenarios:workmanager-leak"))
    implementation(project(":scenarios:bitmap-leak"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
}
