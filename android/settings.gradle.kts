pluginManagement {
    // Resolve KSP's implementation module directly from Maven Central.
    // A clean GitHub Actions runner can fail to find the plugin marker even
    // though 1.9.24-1.0.20's implementation artifact is published.
    // Keep Kotlin/KSP versions unchanged; do not upgrade the compiler here.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        // Independent Google Cloud mirror of Maven Central (documented by Google).
        // Prevents a transient Maven Central outage from blocking clean CI runners.
        // Restrict the mirror to KSP coordinates; all other dependencies follow
        // the existing official Maven Central/Google repository precedence.
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
            content { includeGroup("com.google.devtools.ksp") }
        }
        // KSP plugin markers are published on Maven Central. Prefer it before
        // Google's plugin mirror to avoid transient Google Maven misses in CI.
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
            content { includeGroup("com.google.devtools.ksp") }
        }
        mavenCentral()
    }
}

rootProject.name = "ALKE"
include(":app")
