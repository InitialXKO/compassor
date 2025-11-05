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
        // 高德地图仓库
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "Compassor"
include(":app")
