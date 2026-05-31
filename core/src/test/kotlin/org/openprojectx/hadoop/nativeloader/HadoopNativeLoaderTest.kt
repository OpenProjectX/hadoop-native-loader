package org.openprojectx.hadoop.nativeloader

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class HadoopNativeLoaderTest {

    @Test
    fun `load extracts artifacts and wires up the jvm`() {
        val home = HadoopNativeLoader.load()
        val bin = File(home, "bin")

        assertAll(
            { assertTrue(bin.isDirectory, "bin directory should exist: $bin") },
            {
                // hadoop.home.dir must point at the extracted home so Hadoop's
                // Shell can locate winutils.exe.
                assertEquals(
                    home.absolutePath,
                    System.getProperty(HadoopNativeLoader.HADOOP_HOME_PROPERTY),
                )
            },
            {
                val libPath = System.getProperty("java.library.path").orEmpty()
                assertTrue(
                    libPath.split(File.pathSeparator).any { it == bin.absolutePath },
                    "java.library.path should contain $bin but was: $libPath",
                )
            },
        )
    }

    @Test
    fun `bundled windows and native artifacts are extracted`() {
        val bin = File(HadoopNativeLoader.load(), "bin")
        assertAll(
            // Windows JNI library and helper.
            { assertTrue(File(bin, "hadoop.dll").isFile, "hadoop.dll should be extracted") },
            { assertTrue(File(bin, "winutils.exe").isFile, "winutils.exe should be extracted") },
            // Linux JNI library.
            { assertTrue(File(bin, "libhadoop.so").isFile, "libhadoop.so should be extracted") },
        )
    }

    @Test
    fun `load is idempotent`() {
        assertEquals(HadoopNativeLoader.load(), HadoopNativeLoader.load())
    }
}
