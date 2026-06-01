plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-gradle-plugin`
}

dependencies {
    // The extractor + the bundled native artifacts. Exposed on the plugin's
    // runtime (and TestKit) classpath so the libs can be unpacked at build time.
    implementation(project(":core"))

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

gradlePlugin {
    plugins {
        create("hadoopNativeLoader") {
            id = "org.openprojectx.hadoop-native-loader"
            implementationClass = "org.openprojectx.hadoop.nativeloader.gradle.HadoopNativeLoaderPlugin"
            displayName = "Hadoop Native Loader"
            description = "Extracts the bundled Hadoop native libraries and puts them on the native " +
                "library path for Test and JavaExec (application run) tasks."
        }
    }
}
