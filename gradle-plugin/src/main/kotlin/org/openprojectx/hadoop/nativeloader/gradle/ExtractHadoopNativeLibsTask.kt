package org.openprojectx.hadoop.nativeloader.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.openprojectx.hadoop.nativeloader.HadoopNativeExtractor

/**
 * Extracts the bundled Hadoop native artifacts into [binDirectory].
 */
@DisableCachingByDefault(because = "Trivial file copy from the classpath; caching adds no value.")
abstract class ExtractHadoopNativeLibsTask : DefaultTask() {

    init {
        group = "hadoop native"
        description = "Extracts the bundled Hadoop native libraries onto disk."
    }

    /** Directory the artifacts are written to (the `bin` directory). */
    @get:OutputDirectory
    abstract val binDirectory: DirectoryProperty

    @TaskAction
    fun extract() {
        val dir = binDirectory.get().asFile
        val written = HadoopNativeExtractor.extract(dir)
        logger.lifecycle("Extracted {} Hadoop native artifact(s) to {}", written.size, dir)
    }
}
