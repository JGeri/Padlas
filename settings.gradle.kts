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

rootProject.name = "Padlas"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:design")
include(":core:database")
include(":core:media")
include(":feature:index")
include(":feature:map")
include(":feature:dedup")
include(":feature:triage")
include(":feature:transcode")
include(":feature:archive")
include(":feature:browse")
include(":feature:sort")
include(":feature:rules")
include(":feature:audit")
