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
        mavenCentral()
    }
}

rootProject.name = "ALKE"
include(":app")
