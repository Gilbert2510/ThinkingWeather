// Optional standalone build: gradle -p backend test installDist (no Android SDK required).
// The Android root build supplies its own plugin management when including :backend.
pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
    plugins { id("org.jetbrains.kotlin.jvm") version "2.4.21" }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}
rootProject.name = "backend"
