package org.openprojectx.hadoop.nativeloader.gradle

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.os.OperatingSystem
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test as JupiterTest

class HadoopNativeLoaderPluginTest {

    private fun nativeEnv(): String = when {
        OperatingSystem.current().isWindows -> "PATH"
        OperatingSystem.current().isMacOsX -> "DYLD_LIBRARY_PATH"
        else -> "LD_LIBRARY_PATH"
    }

    @JupiterTest
    fun `registers the extraction task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.openprojectx.hadoop-native-loader")

        assertNotNull(project.tasks.findByName(HadoopNativeLoaderPlugin.EXTRACT_TASK_NAME))
    }

    @JupiterTest
    fun `wires the native path and hadoop home into Test tasks`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.openprojectx.hadoop-native-loader")
        val test = project.tasks.create("verify", Test::class.java)

        (project as ProjectInternal).evaluate() // run afterEvaluate hooks

        val onPath = (test.environment[nativeEnv()] as String)
        assertTrue(onPath.contains("hadoop-native"), "native path should include the extraction dir, was: $onPath")
        assertTrue(
            test.systemProperties["hadoop.home.dir"].toString().contains("hadoop-native"),
            "hadoop.home.dir should be set",
        )
        assertTrue(test.taskDependencies.getDependencies(test).any { it.name == HadoopNativeLoaderPlugin.EXTRACT_TASK_NAME })
    }

    @JupiterTest
    fun `wires the native path into JavaExec tasks`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.openprojectx.hadoop-native-loader")
        val run = project.tasks.create("runApp", JavaExec::class.java)

        (project as ProjectInternal).evaluate()

        val onPath = (run.environment[nativeEnv()] as String)
        assertTrue(onPath.contains("hadoop-native"), "native path should include the extraction dir, was: $onPath")
    }
}
