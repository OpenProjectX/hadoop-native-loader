package org.openprojectx.hadoop.nativeloader.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.JavaForkOptions
import java.io.File

/**
 * Extracts the bundled Hadoop native libraries and puts them on the native
 * library search path for the project's `Test` and `JavaExec` (e.g. the
 * `application` plugin's `run`) tasks, so Hadoop's `NativeCodeLoader` and
 * `Shell` find `libhadoop.so` / `hadoop.dll` / `winutils.exe` without any
 * manual setup.
 *
 * ### Why a Gradle plugin
 *
 * On JDK 9+ the `System.loadLibrary` search path is captured **once at JVM
 * startup** (`java.library.path` is frozen into a `final` array) and cannot be
 * changed from inside the running JVM. The only reliable way to add a directory
 * is to seed the OS dynamic-loader environment variable **at launch**:
 * `LD_LIBRARY_PATH` (Linux), `PATH` (Windows) or `DYLD_LIBRARY_PATH` (macOS).
 * A Gradle plugin is the natural place to do that, because it controls how the
 * test / application JVMs are forked.
 *
 * The directory is scanned at load time, so it is fine that the extraction task
 * only populates it during the build, before the forked JVM runs.
 */
class HadoopNativeLoaderPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("hadoopNativeLoader", HadoopNativeLoaderExtension::class.java)
        extension.outputDirectory.convention(project.layout.buildDirectory.dir("hadoop-native"))
        extension.configureTestTasks.convention(true)
        extension.configureJavaExecTasks.convention(true)
        extension.setHadoopHomeDir.convention(true)

        val binDirectory = extension.outputDirectory.dir("bin")

        val extractTask = project.tasks.register(
            EXTRACT_TASK_NAME,
            ExtractHadoopNativeLibsTask::class.java,
        ) { task ->
            task.binDirectory.set(binDirectory)
        }

        // Wire tasks once the extension has been configured by the build script.
        project.afterEvaluate {
            val home = extension.outputDirectory.get().asFile
            val bin = binDirectory.get().asFile
            val envName = nativePathEnvironmentVariable()
            val envValue = prependToEnv(envName, bin)
            val setHome = extension.setHadoopHomeDir.get()

            if (extension.configureTestTasks.get()) {
                project.tasks.withType(Test::class.java).configureEach { task ->
                    task.dependsOn(extractTask)
                    configureForkOptions(task, envName, envValue, setHome, home)
                }
            }
            if (extension.configureJavaExecTasks.get()) {
                project.tasks.withType(JavaExec::class.java).configureEach { task ->
                    task.dependsOn(extractTask)
                    configureForkOptions(task, envName, envValue, setHome, home)
                }
            }
        }
    }

    private fun configureForkOptions(
        fork: JavaForkOptions,
        envName: String,
        envValue: String,
        setHome: Boolean,
        home: File,
    ) {
        // Seed the OS dynamic-loader path; it is folded into java.library.path
        // when the forked JVM starts.
        fork.environment(envName, envValue)
        if (setHome) {
            // hadoop.home.dir lets org.apache.hadoop.util.Shell locate winutils.
            fork.systemProperty("hadoop.home.dir", home.absolutePath)
        }
    }

    /** Prepends [dir] to the current value of environment variable [name]. */
    private fun prependToEnv(name: String, dir: File): String {
        val existing = System.getenv(name)?.takeIf { it.isNotBlank() }
        return listOfNotNull(dir.absolutePath, existing).joinToString(File.pathSeparator)
    }

    private fun nativePathEnvironmentVariable(): String {
        val os = OperatingSystem.current()
        return when {
            os.isWindows -> "PATH"
            os.isMacOsX -> "DYLD_LIBRARY_PATH"
            else -> "LD_LIBRARY_PATH"
        }
    }

    companion object {
        const val EXTRACT_TASK_NAME = "extractHadoopNativeLibs"
    }
}
