package com.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hadoop.util.NativeCodeLoader;
import org.junit.jupiter.api.Test;

/**
 * Verifies the plugin also wires the native path into {@code Test} tasks: the
 * real Hadoop client must report the native library as loaded.
 */
class HadoopNativeExampleTest {

    @Test
    void nativeCodeIsLoaded() {
        assertTrue(
                NativeCodeLoader.isNativeCodeLoaded(),
                "the plugin should have put libhadoop.so/hadoop.dll on the native library path");
    }
}
