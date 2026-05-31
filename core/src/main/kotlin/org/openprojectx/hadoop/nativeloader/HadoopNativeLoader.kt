package org.openprojectx.hadoop.nativeloader

import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads the bundled Hadoop native artifacts (`hadoop.dll`, `winutils.exe`,
 * `libhdfspp.so`, ...) and wires up the JVM so that the Hadoop client picks
 * them up.
 *
 * The whole point of this library is *ordering*: Hadoop resolves
 * `hadoop.home.dir`, `winutils` and the `hadoop` native library inside the
 * static initializers of [`org.apache.hadoop.util.Shell`] and
 * [`org.apache.hadoop.util.NativeCodeLoader`]. Those run the very first time
 * any Hadoop class is touched and the result is cached for the lifetime of the
 * JVM. If the environment is not prepared *before* that happens, you are stuck
 * with the dreaded `java.io.FileNotFoundException: HADOOP_HOME and
 * hadoop.home.dir are unset` / `Unable to load native-hadoop library` for the
 * rest of the process.
 *
 * Call [load] as early as possible (a JVM agent, a static block, or — for tests
 * — the bundled JUnit 5 integration) and it will:
 *
 *  1. Extract every artifact under `resources/lib` into a stable
 *     `<home>/bin` directory on disk.
 *  2. Point `hadoop.home.dir` at `<home>` (this is a plain system property and
 *     takes effect at runtime — it is what lets Hadoop's `Shell` locate
 *     `winutils.exe`).
 *  3. Prepend `<home>/bin` to the `java.library.path` system property.
 *  4. Eagerly `System.load` the platform native library by absolute path, so
 *     the bits are mapped into *this* process before Hadoop asks for them.
 *
 * ### Making Hadoop's own `System.loadLibrary("hadoop")` succeed
 *
 * On JDK 9+ the `System.loadLibrary` search path is frozen at JVM startup and
 * cannot be changed from inside the JVM (see [prependLibraryPath]). So for
 * Hadoop's `NativeCodeLoader` to load `libhadoop.so` / `hadoop.dll`, the
 * `<home>/bin` directory must be on the OS dynamic-loader path **when the JVM is
 * launched**:
 *
 *  * Linux  — `LD_LIBRARY_PATH`
 *  * Windows — `PATH`
 *  * macOS  — `DYLD_LIBRARY_PATH`
 *
 * Set [TARGET_DIR_PROPERTY] to a fixed directory and point the relevant env var
 * at its `bin` sub-directory; the loader then populates it before any test (or
 * Hadoop client) touches the native code. The bundled JUnit 5 build wiring does
 * exactly this.
 *
 * The method is idempotent and thread-safe; repeated calls are cheap no-ops.
 */
object HadoopNativeLoader {

    /** Classpath directory that holds the bundled native artifacts. */
    private const val RESOURCE_DIR = "lib"

    /**
     * System property pointing at the directory the artifacts should be
     * extracted to. Defaults to a per-user temp directory. Set it if you need a
     * predictable location (e.g. a shared, pre-warmed cache).
     */
    const val TARGET_DIR_PROPERTY = "hadoop.native.loader.dir"

    /** System property Hadoop itself reads to locate its home directory. */
    const val HADOOP_HOME_PROPERTY = "hadoop.home.dir"

    private val logger = System.getLogger(HadoopNativeLoader::class.java.name)

    private val started = AtomicBoolean(false)

    @Volatile
    private var resolvedHome: File? = null

    /** The directory `hadoop.home.dir` was pointed at, once [load] has run. */
    val hadoopHome: File?
        get() = resolvedHome

    /**
     * Prepares the JVM for the bundled Hadoop native libraries. Safe to call
     * multiple times and from multiple threads — only the first invocation does
     * any work.
     *
     * @return the resolved Hadoop home directory.
     */
    @JvmStatic
    fun load(): File {
        resolvedHome?.let { return it }
        if (!started.compareAndSet(false, true)) {
            // Another thread is (or already finished) loading; wait for it.
            synchronized(this) { return resolvedHome ?: error("Hadoop native loading failed") }
        }
        return synchronized(this) {
            try {
                doLoad().also { resolvedHome = it }
            } catch (t: Throwable) {
                started.set(false)
                throw t
            }
        }
    }

    private fun doLoad(): File {
        val home = resolveHomeDir()
        val bin = File(home, "bin").apply { mkdirs() }

        val extracted = extractArtifacts(bin)
        logger.log(System.Logger.Level.INFO, "Extracted ${extracted.size} Hadoop native artifact(s) to $bin")

        // 1) Let Hadoop's Shell/HADOOP_HOME resolution find winutils.exe & co.
        setSystemPropertyIfAbsent(HADOOP_HOME_PROPERTY, home.absolutePath)

        // 2) Make the bin directory discoverable by System.loadLibrary("hadoop").
        prependLibraryPath(bin.absolutePath)

        // 3) Eagerly map the platform native library so it is in memory before
        //    Hadoop's NativeCodeLoader static block runs.
        eagerlyLoadNativeLibrary(bin)

        return home
    }

    private fun resolveHomeDir(): File {
        System.getProperty(TARGET_DIR_PROPERTY)?.takeIf { it.isNotBlank() }?.let {
            return File(it).absoluteFile
        }
        val base = File(System.getProperty("java.io.tmpdir"), "hadoop-native-loader")
        // Version the directory so upgrading the jar invalidates a stale extraction.
        return File(base, version()).absoluteFile
    }

    private fun version(): String =
        HadoopNativeLoader::class.java.`package`?.implementationVersion?.takeIf { it.isNotBlank() }
            ?: "dev"

    /**
     * Copies every file under [RESOURCE_DIR] on the classpath into [targetDir].
     * Works whether the resources live in an exploded directory (IDE / Gradle
     * run) or inside a jar.
     */
    private fun extractArtifacts(targetDir: File): List<File> {
        val names = listArtifactNames()
        if (names.isEmpty()) {
            logger.log(
                System.Logger.Level.WARNING,
                "No Hadoop native artifacts found on the classpath under '$RESOURCE_DIR/'",
            )
            return emptyList()
        }
        return names.mapNotNull { name -> extractOne(name, targetDir) }
    }

    private fun extractOne(name: String, targetDir: File): File? {
        val resource = "$RESOURCE_DIR/$name"
        val stream = classLoader().getResourceAsStream(resource) ?: run {
            logger.log(System.Logger.Level.WARNING, "Resource '$resource' disappeared during extraction")
            return null
        }
        val target = File(targetDir, name)
        stream.use { input ->
            // Extract to a temp file then atomically move so concurrent JVMs
            // never observe a half-written artifact.
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
            // hadoop.dll/winutils.exe are no-ops here; native .so and winutils
            // need the execute bit on POSIX systems.
            target.setExecutable(true, false)
        }
        return target
    }

    private fun moveIntoPlace(tmp: Path, target: Path) {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // ATOMIC_MOVE/REPLACE_EXISTING is not supported everywhere; fall back.
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                // Target already exists (another JVM won the race) — that's fine.
                Files.deleteIfExists(tmp)
            }
        }
    }

    /** Enumerates the artifact file names bundled under [RESOURCE_DIR]. */
    private fun listArtifactNames(): List<String> {
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

    private fun listJarArtifacts(url: java.net.URL): List<String> {
        val connection = url.openConnection() as JarURLConnection
        val prefix = "$RESOURCE_DIR/"
        connection.useCaches = false
        return connection.jarFile.use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(prefix) }
                .map { it.name.substring(prefix.length) }
                // Only direct children, not files in nested sub-directories.
                .filter { it.isNotEmpty() && !it.contains('/') }
                .toList()
                .sorted()
        }
    }

    private fun setSystemPropertyIfAbsent(key: String, value: String) {
        val existing = System.getProperty(key)
        if (existing.isNullOrBlank()) {
            System.setProperty(key, value)
            logger.log(System.Logger.Level.INFO, "Set -D$key=$value")
        } else {
            logger.log(System.Logger.Level.INFO, "Leaving existing -D$key=$existing untouched")
        }
    }

    /**
     * Prepends [dir] to the `java.library.path` system property.
     *
     * Be aware of an important JVM limitation: on JDK 9+ the search path used by
     * [System.loadLibrary] is captured from `java.library.path` **once, at JVM
     * startup** (`jdk.internal.util.StaticProperty`) into an unmodifiable,
     * `final` array. Mutating the system property afterwards therefore has *no*
     * effect on `System.loadLibrary` (the legacy `ClassLoader.usr_paths` reset
     * trick no longer works either — those fields were removed). The only
     * reliable way to make Hadoop's `NativeCodeLoader.System.loadLibrary("hadoop")`
     * find the extracted library is to have its directory on the OS dynamic
     * loader path **at launch** — `LD_LIBRARY_PATH` (Linux), `PATH` (Windows) or
     * `DYLD_LIBRARY_PATH` (macOS) pointing at [hadoopHome]`/bin`.
     *
     * We still update the property because (a) it is correct on legacy JDK 8,
     * (b) some libraries read it directly, and (c) it documents intent. For the
     * current process we additionally [eagerlyLoadNativeLibrary] by absolute
     * path, which does not depend on the search path at all.
     */
    private fun prependLibraryPath(dir: String) {
        val current = System.getProperty("java.library.path").orEmpty()
        if (current.split(File.pathSeparator).any { it == dir }) return

        val updated = if (current.isBlank()) dir else dir + File.pathSeparator + current
        System.setProperty("java.library.path", updated)
    }

    private fun eagerlyLoadNativeLibrary(bin: File) {
        val fileName = System.mapLibraryName("hadoop") // hadoop.dll / libhadoop.so / libhadoop.dylib
        val lib = File(bin, fileName)
        if (!lib.isFile) {
            logger.log(
                System.Logger.Level.DEBUG,
                "No bundled '$fileName' for ${Os.current}; relying on Hadoop to load native libs lazily",
            )
            return
        }
        try {
            System.load(lib.absolutePath)
            logger.log(System.Logger.Level.INFO, "Eagerly loaded native library $lib")
        } catch (t: UnsatisfiedLinkError) {
            logger.log(
                System.Logger.Level.WARNING,
                "Failed to eagerly load $lib; Hadoop will retry via java.library.path: ${t.message}",
            )
        }
    }

    private fun classLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: HadoopNativeLoader::class.java.classLoader
}
