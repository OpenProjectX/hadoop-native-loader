pluginManagement {
    repositories {
        val isCi = System.getenv().containsKey("CI") ||
                System.getenv().containsKey("GITHUB_ACTIONS") ||
                System.getenv().containsKey("JENKINS_HOME")
        if (!isCi) {
            maven(url = "https://mirrors.tencent.com/nexus/repository/maven-public/")
            maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        }
        gradlePluginPortal()
        mavenCentral()
    }
    // Resolve the `org.openprojectx.hadoop-native-loader` plugin from the parent
    // build's source (the :gradle-plugin project) instead of a published artifact.
    includeBuild("..")
}

dependencyResolutionManagement {
    repositories {
        val isCi = System.getenv().containsKey("CI") ||
                System.getenv().containsKey("GITHUB_ACTIONS") ||
                System.getenv().containsKey("JENKINS_HOME")
        if (!isCi) {
            maven(url = "https://mirrors.tencent.com/nexus/repository/maven-public/")
            maven(url = "https://maven.aliyun.com/repository/public/")
        }
        mavenCentral()
    }
}

rootProject.name = "hadoop-native-loader-example"
