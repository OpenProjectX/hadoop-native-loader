# hadoop-native-loader

Load the bundled Hadoop native artifacts (`hadoop.dll`, `winutils.exe`,
`libhadoop.so`, …) and wire up the JVM **before the Hadoop client initializes**,
so the native code is actually used instead of falling back to the pure-Java
implementations.

Bundled artifacts target **Hadoop 3.4.2** and live in
[`core/src/main/resources/lib`](core/src/main/resources/lib).

## Why this exists

Hadoop resolves `hadoop.home.dir`, `winutils`, and the `hadoop` native library
inside the **static initializers** of `org.apache.hadoop.util.Shell` and
`org.apache.hadoop.util.NativeCodeLoader`. Those run the first time *any* Hadoop
class is touched, and the result is cached for the life of the JVM. If the
environment is not prepared *before* that happens, you are stuck with the
familiar errors for the rest of the process:

```
java.io.FileNotFoundException: HADOOP_HOME and hadoop.home.dir are unset
WARN  NativeCodeLoader - Unable to load native-hadoop library for your platform...
                          using builtin-java classes where applicable
```

This library extracts the right artifacts to disk and configures the JVM **as
early as possible** so the Hadoop client finds everything.

## Modules

| Module   | Artifact                                                  | Purpose                                                                 |
|----------|-----------------------------------------------------------|-------------------------------------------------------------------------|
| `core`   | `org.openprojectx.hadoop.native.loader.core:core`         | `HadoopNativeLoader` — extracts the libs and configures the JVM.        |
| `junit5` | `org.openprojectx.hadoop.native.loader.core:junit5`       | Hooks the loader into the JUnit 5 lifecycle so it runs before any test. |

## Usage

### Programmatic (`core`)

Call `HadoopNativeLoader.load()` once, as early as possible — ideally before any
Hadoop class is referenced (a `main()` entry, a static initializer, or a
servlet/Spring bootstrap):

```kotlin
import org.openprojectx.hadoop.nativeloader.HadoopNativeLoader

fun main() {
    HadoopNativeLoader.load()   // idempotent, thread-safe
    // ... now use the Hadoop client
}
```

`load()`:

1. Extracts every artifact under `resources/lib` into a stable `<home>/bin`.
2. Sets `hadoop.home.dir` to `<home>` (lets `Shell` locate `winutils.exe`).
3. Prepends `<home>/bin` to the `java.library.path` system property.
4. Eagerly `System.load`s the platform native library by absolute path.

### Tests (`junit5`)

Add the `junit5` artifact to your test classpath. A
`LauncherSessionListener` registered via `META-INF/services` runs **when the
JUnit launcher session opens — before any test class is discovered or loaded**,
which is the earliest hook JUnit offers. No annotation required:

```kotlin
class MyHdfsTest {
    @Test
    fun usesNativeHadoop() {
        // HadoopNativeLoader.load() already ran before this class loaded.
    }
}
```

If you prefer an explicit, per-class opt-in instead, use the bundled extension:

```kotlin
@ExtendWith(HadoopNativeLoaderExtension::class)
class MyHdfsTest { /* ... */ }
```

## Important: making `NativeCodeLoader` load the native library

> **On JDK 9+ you cannot change `System.loadLibrary`'s search path from inside a
> running JVM.**

`java.library.path` is captured *once at JVM startup* into an immutable, `final`
array (`jdk.internal.loader.NativeLibraries$LibraryPaths`). Mutating the system
property afterwards has no effect, and the old `ClassLoader.usr_paths` reset
trick no longer works (those fields were removed). So setting `hadoop.home.dir`
is enough for `winutils` (a plain property), but Hadoop's
`NativeCodeLoader.System.loadLibrary("hadoop")` will still fail unless the
extraction directory is on the **OS dynamic-loader path at launch**:

| OS      | Environment variable  |
|---------|-----------------------|
| Linux   | `LD_LIBRARY_PATH`     |
| Windows | `PATH`                |
| macOS   | `DYLD_LIBRARY_PATH`   |

The directory is *scanned at load time*, so it is fine that the loader only
populates it after the JVM is already running — as long as that happens before
the Hadoop client runs. The recipe:

1. Point the loader at a **fixed** directory with `-Dhadoop.native.loader.dir=<dir>`.
2. Put `<dir>/bin` on the relevant env var at launch.

This project's Gradle build does exactly that for its own test tasks — see
[`buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`](buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts):

```kotlin
tasks.withType<Test>().configureEach {
    val nativeHome = layout.buildDirectory.dir("hadoop-native").get().asFile
    val nativeBin = nativeHome.resolve("bin")
    systemProperty("hadoop.native.loader.dir", nativeHome.absolutePath)
    doFirst { nativeBin.mkdirs() }

    val env = when {                       // OS dynamic-loader path
        os.contains("win") -> "PATH"
        os.contains("mac") -> "DYLD_LIBRARY_PATH"
        else               -> "LD_LIBRARY_PATH"
    }
    environment(env, nativeBin.absolutePath + File.pathSeparator + (System.getenv(env) ?: ""))
}
```

For a normal application, set the env var the same way before launching the JVM
(e.g. in your launch script, `Dockerfile`, or run configuration).

## Configuration

| System property              | Default                                        | Description                                              |
|------------------------------|------------------------------------------------|----------------------------------------------------------|
| `hadoop.native.loader.dir`   | `${java.io.tmpdir}/hadoop-native-loader/<ver>` | Directory the artifacts are extracted into.              |
| `hadoop.home.dir`            | *(set by the loader)*                          | Hadoop home. The loader only sets it when it is unset.   |

## Building

```bash
./gradlew build      # compile + test both modules
./gradlew test       # run tests only
```

Requires a JDK 17 toolchain (configured via the Gradle Kotlin convention plugin).

## License

[Apache License 2.0](LICENSE).
