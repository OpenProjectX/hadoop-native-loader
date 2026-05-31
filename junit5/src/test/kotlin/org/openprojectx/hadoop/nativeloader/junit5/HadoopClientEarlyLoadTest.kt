package org.openprojectx.hadoop.nativeloader.junit5

import org.apache.hadoop.util.NativeCodeLoader
import org.apache.hadoop.util.Shell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.openprojectx.hadoop.nativeloader.HadoopNativeLoader
import java.io.File

/**
 * End-to-end check of the JUnit 5 integration against the real Hadoop client.
 *
 * Crucially, this test never calls [HadoopNativeLoader.load] itself — the
 * [HadoopNativeLoaderLauncherSessionListener] must have prepared everything when
 * the launcher session opened, *before* this (or any) test class touched
 * Hadoop. We then reference Hadoop's [NativeCodeLoader] and confirm it picked up
 * the bundled library.
 */
class HadoopClientEarlyLoadTest {

    @Test
    fun `hadoop Shell sees the home dir prepared by the listener`() {
        // No load() call here: the LauncherSessionListener must have configured
        // hadoop.home.dir before this test, so the real Hadoop client resolves
        // it. Works on every OS.
        val home = HadoopNativeLoader.hadoopHome
        assertNotNull(home, "LauncherSessionListener should have run before any test")
        assertEquals(home!!.canonicalFile, File(Shell.getHadoopHome()).canonicalFile)
    }

    @Test
    fun `native environment is ready before the hadoop client is used`() {
        val home = HadoopNativeLoader.hadoopHome
        assertNotNull(home, "LauncherSessionListener should have run before any test")

        val mappedLib = File(File(home, "bin"), System.mapLibraryName("hadoop"))
        assumeTrue(
            mappedLib.isFile,
            "No bundled native 'hadoop' library for ${System.getProperty("os.name")} " +
                "(expected ${mappedLib.name}); skipping the native-load assertion on this platform",
        )

        assertTrue(
            NativeCodeLoader.isNativeCodeLoaded(),
            "Hadoop's NativeCodeLoader did not load the native library prepared by the session listener",
        )
    }
}
