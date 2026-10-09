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

rootProject.name = "Urkaaaz"
include(
    ":game-contracts",
    ":game-domain",
    ":game-simulation",
    ":game-campaign",
    ":game-ai",
    ":game-application",
    ":android-app",
)
