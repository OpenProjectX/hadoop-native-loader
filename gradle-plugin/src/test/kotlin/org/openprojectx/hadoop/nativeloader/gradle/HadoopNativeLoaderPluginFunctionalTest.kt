package org.openprojectx.hadoop.nativeloader.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * End-to-end check: a consumer build that applies the plugin and the
 * `application` plugin must be able to `System.loadLibrary("hadoop")` from its
 * `run` task — exactly what Hadoop's `NativeCodeLoader` does. The plugin is
 * responsible for extracting the library and putting it on the native path.
 */
class HadoopNativeLoaderPluginFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun write(path: String, content: String) {
        File(projectDir, path).apply { parentFile.mkdirs() }.writeText(content)
    }

    @Test
    fun `application run loads the bundled native hadoop library`() {
        write("settings.gradle.kts", """rootProject.name = "consumer"""")
        write(
            "build.gradle.kts",
            """
            plugins {
                application
                id("org.openprojectx.hadoop-native-loader")
            }
            application {
                mainClass.set("Main")
            }
            """.trimIndent(),
        )
        write(
            "src/main/java/Main.java",
            """
            public class Main {
                public static void main(String[] args) {
                    System.loadLibrary("hadoop"); // throws UnsatisfiedLinkError if not on the native path
                    System.out.println("LOADED_HADOOP_OK from " + System.getProperty("hadoop.home.dir"));
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("run", "--stacktrace")
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":run")?.outcome,
            "`run` task should succeed (native library loaded). Output:\n${result.output}",
        )
        assertTrue(
            result.output.contains("LOADED_HADOOP_OK"),
            "expected the application to load the native library. Output:\n${result.output}",
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":${HadoopNativeLoaderPlugin.EXTRACT_TASK_NAME}")?.outcome,
            "extraction task should have run before the application",
        )
    }
}
