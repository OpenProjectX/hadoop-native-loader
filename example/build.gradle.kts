plugins {
    application
    // Provided by the parent build via includeBuild("..") in settings.gradle.kts.
    id("org.openprojectx.hadoop-native-loader")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("org.apache.hadoop:hadoop-common:3.4.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.example.HadoopNativeExample")
}

tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
}

// The plugin already extracts the libs and wires the native path into `run` and
// `test`. Override any defaults here if needed, e.g.:
// hadoopNativeLoader {
//     outputDirectory.set(layout.buildDirectory.dir("hadoop-native"))
//     setHadoopHomeDir.set(true)
// }
