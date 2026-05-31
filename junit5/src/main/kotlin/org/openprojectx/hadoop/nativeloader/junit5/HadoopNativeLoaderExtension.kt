package org.openprojectx.hadoop.nativeloader.junit5

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.openprojectx.hadoop.nativeloader.HadoopNativeLoader

/**
 * JUnit Jupiter extension that loads the bundled Hadoop native libraries before
 * a test class runs.
 *
 * Prefer the zero-configuration [HadoopNativeLoaderLauncherSessionListener]
 * (which fires even earlier, at session start). This extension exists for cases
 * where you want an explicit, per-class opt-in:
 *
 * ```kotlin
 * @ExtendWith(HadoopNativeLoaderExtension::class)
 * class MyHdfsTest { ... }
 * ```
 *
 * It can also be auto-detected for every test by enabling
 * `junit.jupiter.extensions.autodetection.enabled=true` (this artifact already
 * registers it as a Jupiter extension service).
 *
 * [HadoopNativeLoader.load] is idempotent, so combining several activation
 * mechanisms is harmless.
 */
class HadoopNativeLoaderExtension : BeforeAllCallback {

    override fun beforeAll(context: ExtensionContext) {
        HadoopNativeLoader.load()
    }
}
