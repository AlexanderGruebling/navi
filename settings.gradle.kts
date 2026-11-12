pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "navi"

include(":app")
include(":core:model")
include(":core:network")
include(":core:datastore")
include(":core:database")
include(":core:data")
include(":core:navigation")
include(":core:download")
include(":core:playback")
include(":core:ui")
include(":feature:login")
include(":feature:home")
include(":feature:search")
include(":feature:library")
include(":feature:detail")
include(":feature:player")
include(":feature:settings")
include(":benchmark")
