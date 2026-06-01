package org.openprojectx.hadoop.nativeloader.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * Configuration for the `org.openprojectx.hadoop-native-loader` plugin.
 *
 * ```kotlin
 * hadoopNativeLoader {
 *     outputDirectory.set(layout.buildDirectory.dir("hadoop-native"))
 *     configureTestTasks.set(true)
 *     configureJavaExecTasks.set(true)
 *     setHadoopHomeDir.set(true)
 * }
 * ```
 */
abstract class HadoopNativeLoaderExtension {

    /**
     * Directory the native artifacts are extracted into; the libraries land in
     * its `bin` sub-directory. Defaults to `build/hadoop-native`.
     */
    abstract val outputDirectory: DirectoryProperty

    /** Whether to wire the native library path into [org.gradle.api.tasks.testing.Test] tasks. Default `true`. */
    abstract val configureTestTasks: Property<Boolean>

    /** Whether to wire the native library path into [org.gradle.api.tasks.JavaExec] tasks. Default `true`. */
    abstract val configureJavaExecTasks: Property<Boolean>

    /** Whether to set the `hadoop.home.dir` system property on configured tasks. Default `true`. */
    abstract val setHadoopHomeDir: Property<Boolean>
}
