plugins {
    id("buildsrc.convention.kotlin-jvm")
    // Generates the Maven plugin descriptor (META-INF/maven/plugin.xml) and the
    // HelpMojo from the @Mojo annotations, so this Maven plugin builds with Gradle.
    id("org.gradlex.maven-plugin-development") version "1.0.3"
}

val mavenVersion = "3.9.11"
val mavenPluginToolsVersion = "3.13.1"

dependencies {
    // The extractor + the bundled native artifacts.
    implementation(project(":core"))

    // Maven APIs are provided by the Maven runtime that loads the plugin.
    compileOnly("org.apache.maven:maven-plugin-api:$mavenVersion")
    compileOnly("org.apache.maven:maven-core:$mavenVersion")
    compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:$mavenPluginToolsVersion")
}

mavenPlugin {
    // `mvn hadoop-native-loader:extract`; also used for the <goalPrefix> in the descriptor.
    goalPrefix.set("hadoop-native-loader")
}
