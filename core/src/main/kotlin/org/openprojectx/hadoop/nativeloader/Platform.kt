package org.openprojectx.hadoop.nativeloader

import java.util.Locale

/**
 * Minimal operating-system detection used to decide which native artifacts are
 * relevant for the current JVM and what the platform-specific Hadoop native
 * library file name looks like.
 */
internal enum class Os {
    WINDOWS,
    LINUX,
    MAC,
    OTHER;

    companion object {
        val current: Os by lazy {
            val name = System.getProperty("os.name", "").lowercase(Locale.ROOT)
            when {
                name.contains("win") -> WINDOWS
                name.contains("mac") || name.contains("darwin") -> MAC
                name.contains("nux") || name.contains("nix") || name.contains("aix") -> LINUX
                else -> OTHER
            }
        }
    }
}
