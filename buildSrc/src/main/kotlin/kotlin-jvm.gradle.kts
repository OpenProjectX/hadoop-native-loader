// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.io.File

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
}

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // --- Hadoop native library wiring -------------------------------------
    // HadoopNativeLoader extracts the bundled native artifacts into this
    // directory at runtime (see HadoopNativeLoader.TARGET_DIR_PROPERTY).
    val nativeHome = layout.buildDirectory.dir("hadoop-native").get().asFile
    val nativeBin = nativeHome.resolve("bin")
    systemProperty("hadoop.native.loader.dir", nativeHome.absolutePath)
    doFirst { nativeBin.mkdirs() }

    // On JDK 9+ the System.loadLibrary search path is frozen at JVM startup and
    // cannot be changed from inside the JVM, so the loader alone cannot make
    // Hadoop's NativeCodeLoader find libhadoop.so/hadoop.dll. We therefore seed
    // the OS dynamic-loader path (folded into java.library.path at launch) with
    // the extraction directory. The directory is scanned at load time, so it is
    // fine that the loader only populates it once the test JVM is already up.
    val osName = System.getProperty("os.name").lowercase()
    val loaderPathEnv = when {
        osName.contains("win") -> "PATH"
        osName.contains("mac") || osName.contains("darwin") -> "DYLD_LIBRARY_PATH"
        else -> "LD_LIBRARY_PATH"
    }
    val existing = System.getenv(loaderPathEnv)?.takeIf { it.isNotBlank() }
    environment(
        loaderPathEnv,
        listOfNotNull(nativeBin.absolutePath, existing).joinToString(File.pathSeparator),
    )
    // ----------------------------------------------------------------------

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}