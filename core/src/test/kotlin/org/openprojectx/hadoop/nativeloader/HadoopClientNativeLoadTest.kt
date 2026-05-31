package org.openprojectx.hadoop.nativeloader

import org.apache.hadoop.util.NativeCodeLoader
import org.apache.hadoop.util.Shell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Exercises the *real* Hadoop client. After [HadoopNativeLoader.load] has run,
 * Hadoop's own `org.apache.hadoop.util.NativeCodeLoader` — the class whose
 * static initializer does `System.loadLibrary("hadoop")` — must be able to find
 * and load the bundled native library via the `java.library.path` entry we
 * added.
 */
class HadoopClientNativeLoadTest {

    @Test
    fun `hadoop NativeCodeLoader loads the bundled native library`() {
        // Must run before NativeCodeLoader is referenced below so its static
        // initializer sees the prepared java.library.path.
        val home = HadoopNativeLoader.load()
        val mappedLib = File(File(home, "bin"), System.mapLibraryName("hadoop"))

        assumeTrue(
            mappedLib.isFile,
            "No bundled native 'hadoop' library for ${System.getProperty("os.name")} " +
                    "(expected ${mappedLib.name}); the JVM-side native lib is only shipped for some platforms",
        )

        assertTrue(
            NativeCodeLoader.isNativeCodeLoaded(),
            "Hadoop reported the native-hadoop library as NOT loaded, even though $mappedLib " +
                    "was placed on java.library.path",
        )

    }

    @Test
    fun `hadoop Shell resolves to the extracted home directory`() {
        val home = HadoopNativeLoader.load()
        // org.apache.hadoop.util.Shell reads hadoop.home.dir, validates the
        // directory and caches it. Getting a value back (instead of the classic
        // "HADOOP_HOME and hadoop.home.dir are unset" failure) proves Hadoop
        // itself consumed what the loader configured. This runs on every OS.
        assertEquals(home.canonicalFile, File(Shell.getHadoopHome()).canonicalFile)
    }
}
