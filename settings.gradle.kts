// 全部依赖走腾讯软件源镜像，避免访问 Google / Maven Central 超时。
// 若你的网络环境可直连官方仓库，把下面两组 url 换回 google() / mavenCentral() 即可。
pluginManagement {
    repositories {
        // Gradle 插件门户镜像（AGP、Kotlin 插件从这里解析）
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/gradle-plugins/") }
        // Google Maven 镜像（androidx / material）
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/google/") }
        // Maven 公共代理（其余第三方库）
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/google/") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/") }
        maven { url = uri("https://mirrors.tencent.com/nexus/repository/gradle-plugins/") }
    }
}
rootProject.name = "ReaderH"
include(":app")
