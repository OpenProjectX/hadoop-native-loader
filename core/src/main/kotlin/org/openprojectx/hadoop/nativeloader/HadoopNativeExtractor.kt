package org.openprojectx.hadoop.nativeloader

import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Extracts the bundled Hadoop native artifacts (`hadoop.dll`, `winutils.exe`,
 * `libhadoop.so`, …) from the classpath onto disk.
 *
 * This is the whole responsibility of the `core` module: get the right files
 * out of the jar and into a directory. *Wiring the JVM* so that Hadoop actually
 * uses them (putting the directory on the native library search path, setting
 * `hadoop.home.dir`) is done by the Gradle plugin, because on JDK 9+ the
 * `System.loadLibrary` search path is frozen at JVM startup and can only be
 * influenced at launch time — see the `gradle-plugin` module.
 *
 * The bundled artifacts live under `resources/[RESOURCE_DIR]` and target a
 * specific Hadoop version (see the project README).
 */
object HadoopNativeExtractor {

    /** Classpath directory that holds the bundled native artifacts. */
    const val RESOURCE_DIR = "lib"

    private val logger = System.getLogger(HadoopNativeExtractor::class.java.name)

    /**
     * Copies every bundled artifact into [targetDir] (created if necessary),
     * overwriting existing files. On POSIX systems the executable bit is set so
     * native libraries and `winutils` can be used directly.
     *
     * Extraction is done via a temp file + atomic move, so concurrent processes
     * never observe a half-written artifact.
     *
     * @return the list of files written into [targetDir].
     */
    fun extract(targetDir: File): List<File> {
        targetDir.mkdirs()
        val names = bundledArtifactNames()
        if (names.isEmpty()) {
            logger.log(
                System.Logger.Level.WARNING,
                "No Hadoop native artifacts found on the classpath under '$RESOURCE_DIR/'",
            )
            return emptyList()
        }
        return names.mapNotNull { extractOne(it, targetDir) }
    }

    /** Names of the artifacts bundled under [RESOURCE_DIR], sorted. */
    fun bundledArtifactNames(): List<String> {
        val url = classLoader().getResource(RESOURCE_DIR) ?: return emptyList()
        return when (url.protocol) {
            "file" -> File(url.toURI()).listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty()
            "jar" -> listJarArtifacts(url)
            else -> {
                logger.log(System.Logger.Level.WARNING, "Unsupported resource protocol '${url.protocol}' for $url")
                emptyList()
            }
        }
    }

    private fun extractOne(name: String, targetDir: File): File? {
        val resource = "$RESOURCE_DIR/$name"
        val stream = classLoader().getResourceAsStream(resource) ?: run {
            logger.log(System.Logger.Level.WARNING, "Resource '$resource' disappeared during extraction")
            return null
        }
        val target = File(targetDir, name)
        stream.use { input ->
            val tmp = Files.createTempFile(targetDir.toPath(), name, ".part")
            try {
                Files.copy(input, tmp, StandardCopyOption.REPLACE_EXISTING)
                moveIntoPlace(tmp, target.toPath())
            } catch (e: IOException) {
                Files.deleteIfExists(tmp)
                throw e
            }
        }
        if (Os.current != Os.WINDOWS) {
            // Native .so/.dylib and winutils need the execute bit on POSIX.
            target.setExecutable(true, false)
        }
        return target
    }

    private fun moveIntoPlace(tmp: Path, target: Path) {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                // Another process won the race; its copy is fine.
                Files.deleteIfExists(tmp)
            }
        }
    }

    private fun listJarArtifacts(url: URL): List<String> {
        val connection = url.openConnection() as JarURLConnection
        val prefix = "$RESOURCE_DIR/"
        connection.useCaches = false
        return connection.jarFile.use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(prefix) }
                .map { it.name.substring(prefix.length) }
                .filter { it.isNotEmpty() && !it.contains('/') }
                .toList()
                .sorted()
        }
    }

    private fun classLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: HadoopNativeExtractor::class.java.classLoader
}
