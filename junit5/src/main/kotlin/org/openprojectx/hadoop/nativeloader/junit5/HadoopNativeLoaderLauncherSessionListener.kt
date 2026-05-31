package org.openprojectx.hadoop.nativeloader.junit5

import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener
import org.openprojectx.hadoop.nativeloader.HadoopNativeLoader

/**
 * Prepares the Hadoop native environment before any test runs.
 *
 * Registered via [ServiceLoader][java.util.ServiceLoader]
 * (`META-INF/services/org.junit.platform.launcher.LauncherSessionListener`),
 * the JUnit Platform invokes [launcherSessionOpened] when the launcher session
 * is opened — *before* test classes are discovered or loaded. This is the
 * earliest hook JUnit offers, which guarantees the bundled `hadoop.dll` /
 * `winutils.exe` / native libraries are wired up before the Hadoop client's
 * `Shell` and `NativeCodeLoader` static initializers ever run.
 *
 * No annotation or `@ExtendWith` is required: dropping the `junit5` artifact on
 * the test classpath is enough.
 */
class HadoopNativeLoaderLauncherSessionListener : LauncherSessionListener {

    override fun launcherSessionOpened(session: LauncherSession) {
        HadoopNativeLoader.load()
    }
}
