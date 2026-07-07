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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "OneMoreCoffee"

include(
    ":app",
    ":core:common",
    ":core:domain",
    ":core:data",
    ":core:ui",
    ":core:social",
    ":feature:map",
    ":feature:list",
    ":feature:stats",
    ":feature:settings",
    ":feature:import",
    ":feature:friends",
)
