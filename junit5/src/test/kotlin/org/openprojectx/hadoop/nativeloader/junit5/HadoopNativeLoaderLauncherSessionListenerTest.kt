package org.openprojectx.hadoop.nativeloader.junit5

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openprojectx.hadoop.nativeloader.HadoopNativeLoader
import java.io.File

/**
 * The [HadoopNativeLoaderLauncherSessionListener] is registered via
 * `META-INF/services` and fires when the launcher session opens. By the time
 * any test method runs, the native environment must therefore already be
 * prepared — this test asserts that without calling [HadoopNativeLoader.load]
 * itself.
 */
class HadoopNativeLoaderLauncherSessionListenerTest {

    @Test
    fun `native environment is prepared before tests run`() {
        val home = HadoopNativeLoader.hadoopHome
        assertNotNull(home, "LauncherSessionListener should have loaded native libs before the test ran")
        assertTrue(File(home, "bin").isDirectory)
        assertTrue(
            System.getProperty(HadoopNativeLoader.HADOOP_HOME_PROPERTY).isNullOrBlank().not(),
            "hadoop.home.dir should be set",
        )
    }
}
