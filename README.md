# hadoop-native-loader

Make the bundled Hadoop native artifacts (`hadoop.dll`, `winutils.exe`,
`libhadoop.so`, …) available to the Hadoop client **without any manual
environment setup**, so the native code is actually used instead of falling back
to the slower pure-Java implementations.

Bundled artifacts target **Hadoop 3.4.2** and live in
[`core/src/main/resources/lib`](core/src/main/resources/lib).

## Why this exists

Hadoop resolves `hadoop.home.dir`, `winutils`, and the `hadoop` native library
inside the **static initializers** of `org.apache.hadoop.util.Shell` and
`org.apache.hadoop.util.NativeCodeLoader`. If the environment is not prepared
*before* those run, you are stuck with the familiar errors for the life of the
JVM:

```
java.io.FileNotFoundException: HADOOP_HOME and hadoop.home.dir are unset
WARN  NativeCodeLoader - Unable to load native-hadoop library for your platform...
                          using builtin-java classes where applicable
```

There is a hard JVM constraint in the way:

> **On JDK 9+ the `System.loadLibrary` search path is frozen at JVM startup.**
> `java.library.path` is captured once into an immutable `final` array
> (`jdk.internal.loader.NativeLibraries$LibraryPaths`), so it **cannot** be
> changed from inside a running JVM (the old `ClassLoader.usr_paths` reset trick
> no longer works — those fields were removed). The only reliable way to add a
> directory is to set the OS dynamic-loader variable **at launch**:
> `LD_LIBRARY_PATH` (Linux), `PATH` (Windows), `DYLD_LIBRARY_PATH` (macOS).

Because that has to happen at JVM launch, the clean fix is a **Gradle plugin**:
it controls how the test / application JVMs are forked, extracts the libraries,
and puts them on the native path automatically.

## Modules

| Module         | Artifact                                                                  | Purpose                                                              |
|----------------|---------------------------------------------------------------------------|---------------------------------------------------------------------|
| `core`         | `org.openprojectx.hadoop.native.loader.core:core`                         | `HadoopNativeExtractor` + the bundled native artifacts.             |
| `gradle-plugin`| plugin id `org.openprojectx.hadoop-native-loader`                         | Extracts the libs and wires them onto the native path of forked JVMs.|
| `maven-plugin` | `org.openprojectx.hadoop.native.loader.core:maven-plugin` (goalPrefix `hadoop-native-loader`) | Same, for Maven Surefire/Failsafe test JVMs.        |

## Usage (Gradle plugin)

```kotlin
plugins {
    application                                 // or your test setup
    id("org.openprojectx.hadoop-native-loader") version "0.1.0-SNAPSHOT"
}
```

That's it. Applying the plugin:

1. Registers `extractHadoopNativeLibs`, which unpacks the bundled artifacts into
   `build/hadoop-native/bin`.
2. For every `Test` and `JavaExec` task (e.g. the `application` plugin's `run`):
   - makes it depend on the extraction task;
   - prepends `build/hadoop-native/bin` to the OS dynamic-loader variable
     (`LD_LIBRARY_PATH` / `PATH` / `DYLD_LIBRARY_PATH`), which is folded into
     `java.library.path` when the JVM starts — so
     `System.loadLibrary("hadoop")` finds the library;
   - sets the `hadoop.home.dir` system property (so `Shell` finds `winutils`).

Then `./gradlew test` and `./gradlew run` get the native Hadoop libraries with
no further configuration.

### Example

A runnable example lives in [`example/`](example). It is a standalone composite
build that consumes the plugin from source (`includeBuild("..")`) and uses the
real Hadoop client. From the repository root:

```bash
./gradlew -p example run     # prints "native-hadoop loaded = true"
./gradlew -p example test    # asserts NativeCodeLoader.isNativeCodeLoaded()
```

### Configuration

```kotlin
hadoopNativeLoader {
    // Where the artifacts are extracted (libraries land in <dir>/bin).
    outputDirectory.set(layout.buildDirectory.dir("hadoop-native"))
    configureTestTasks.set(true)      // wire Test tasks      (default true)
    configureJavaExecTasks.set(true)  // wire JavaExec tasks  (default true)
    setHadoopHomeDir.set(true)        // set hadoop.home.dir  (default true)
}
```

## Usage (Maven plugin)

For Maven builds, the `hadoop-native-loader-maven-plugin` does the equivalent
for Surefire/Failsafe. Its `extract` goal unpacks the libraries and — the same
way the JaCoCo agent works — prepends `-Djava.library.path=<bin>` and
`-Dhadoop.home.dir=<dir>` to the `argLine` property, which the forked test JVM
reads at launch.

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.openprojectx.hadoop.native.loader.core</groupId>
      <artifactId>maven-plugin</artifactId>
      <version>0.1.1-SNAPSHOT</version>
      <executions>
        <execution>
          <goals><goal>extract</goal></goals> <!-- bound to the `initialize` phase -->
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Then `mvn test` gets the native Hadoop libraries with no further setup. Goal
parameters: `hadoopNativeLoader.outputDirectory`, `hadoopNativeLoader.propertyName`
(default `argLine`), `hadoopNativeLoader.setHadoopHomeDir`, `hadoopNativeLoader.skip`.

> If you configure Surefire's `argLine` yourself, keep this value with late
> evaluation: `<argLine>@{argLine} ...your args...</argLine>`.

The `maven-plugin` module is a normal Gradle subproject — it is built (and
released) by the Gradle build via the GradleX `maven-plugin-development` plugin,
which generates the Maven plugin descriptor from the `@Mojo` annotations. Install
it locally with `./gradlew :maven-plugin:publishToMavenLocal`. A runnable Maven
example is in [`maven-example/`](maven-example): after the local install, run
`mvn -f maven-example/pom.xml test`.

## Using `core` directly

If you are not using the Gradle plugin, the `core` module just extracts the
artifacts; you are then responsible for putting the directory on the native path
at launch (see the constraint above).

```kotlin
import org.openprojectx.hadoop.nativeloader.HadoopNativeExtractor
import java.io.File

val bin = File(System.getProperty("java.io.tmpdir"), "hadoop-native/bin")
HadoopNativeExtractor.extract(bin)
// then launch your JVM with e.g. LD_LIBRARY_PATH=<bin> (Linux),
// or set -Dhadoop.home.dir=<bin.parent> for winutils.
```

## Building & publishing

```bash
./gradlew build                # compile + test all modules (core, gradle-plugin, maven-plugin)
./gradlew publishToMavenLocal  # install core + both plugins into ~/.m2
```

The release workflow ([`.github/workflows/release.yml`](.github/workflows/release.yml))
publishes to Maven Central via the `net.researchgate.release` +
`io.github.gradle-nexus.publish-plugin` setup.

Requires a JDK 17 toolchain.

## License

[Apache License 2.0](LICENSE).
