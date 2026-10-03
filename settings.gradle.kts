pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "NotificationPipelineConstructor"

include(":core:model")
include(":core:text")
include(":domain")
include(":core:storage")
include(":ingest:notification")
include(":ingest:sms")
include(":ingest:media")
include(":ui:timeline")
include(":classify:rules")
include(":extract:finance")
include(":extract:universal")
include(":induction")
include(":pipeline:dsl")
include(":pipeline:nodes-api")
include(":pipeline:compiler")
include(":pipeline:runtime")
include(":feature:replay")
include(":app")
