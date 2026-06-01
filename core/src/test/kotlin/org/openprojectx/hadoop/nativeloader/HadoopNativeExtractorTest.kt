package org.openprojectx.hadoop.nativeloader

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HadoopNativeExtractorTest {

    @Test
    fun `bundled artifact names include the jni libraries`() {
        val names = HadoopNativeExtractor.bundledArtifactNames()
        assertAll(
            { assertTrue(names.contains("hadoop.dll"), "expected hadoop.dll in $names") },
            { assertTrue(names.contains("winutils.exe"), "expected winutils.exe in $names") },
            { assertTrue(names.contains("libhadoop.so"), "expected libhadoop.so in $names") },
        )
    }

    @Test
    fun `extract writes every bundled artifact to the target directory`(@TempDir target: File) {
        val written = HadoopNativeExtractor.extract(target)

        assertAll(
            { assertTrue(written.isNotEmpty(), "nothing was extracted") },
            { assertTrue(File(target, "hadoop.dll").isFile, "hadoop.dll should be extracted") },
            { assertTrue(File(target, "winutils.exe").isFile, "winutils.exe should be extracted") },
            { assertTrue(File(target, "libhadoop.so").isFile, "libhadoop.so should be extracted") },
            {
                assertTrue(
                    written.map { it.name }.toSet() == HadoopNativeExtractor.bundledArtifactNames().toSet(),
                    "extracted files should match the bundled artifact names",
                )
            },
        )
    }

    @Test
    fun `extract is idempotent`(@TempDir target: File) {
        val first = HadoopNativeExtractor.extract(target).map { it.name }.toSet()
        val second = HadoopNativeExtractor.extract(target).map { it.name }.toSet()
        assertTrue(first == second && first.isNotEmpty())
    }
}
