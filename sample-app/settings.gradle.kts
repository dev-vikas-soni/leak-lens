pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LeakLensSample"
include(":app")
include(":core:common")
include(":scenarios:activity-leak")
include(":scenarios:compose-leak")
include(":scenarios:flow-leak")
include(":scenarios:fragment-leak")
include(":scenarios:singleton-leak")
include(":scenarios:workmanager-leak")
include(":scenarios:bitmap-leak")
