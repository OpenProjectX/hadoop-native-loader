package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.apache.hadoop.util.NativeCodeLoader;
import org.apache.hadoop.util.Shell;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the {@code hadoop-native-loader} Maven plugin wired the test
 * JVM for the real Hadoop client: {@code NativeCodeLoader} reports the native
 * library as loaded, and {@code Shell} resolves the Hadoop home set by the
 * plugin (instead of failing with "HADOOP_HOME and hadoop.home.dir are unset").
 */
class HadoopNativeMavenTest {

    @Test
    void nativeCodeIsLoaded() {
        System.out.println("hadoop.home.dir      = " + System.getProperty("hadoop.home.dir"));
        System.out.println("java.library.path    = " + System.getProperty("java.library.path"));
        assertTrue(
                NativeCodeLoader.isNativeCodeLoaded(),
                "the Maven plugin should have put libhadoop.so/hadoop.dll on java.library.path");
    }

    @Test
    void shellResolvesTheHadoopHomeSetByThePlugin() throws Exception {
        File expected = new File(System.getProperty("hadoop.home.dir"));
        System.out.println("Shell.getHadoopHome  = " + Shell.getHadoopHome());
        assertEquals(expected.getCanonicalFile(), new File(Shell.getHadoopHome()).getCanonicalFile());
    }
}
