plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.github.devvikassoni.leaklens.sample.scenarios.fragment"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation("androidx.fragment:fragment-ktx:1.8.1")
}
