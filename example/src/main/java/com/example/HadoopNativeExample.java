package com.example;

import org.apache.hadoop.util.NativeCodeLoader;
import org.apache.hadoop.util.Shell;

/**
 * Demonstrates that the {@code org.openprojectx.hadoop-native-loader} Gradle
 * plugin makes the bundled Hadoop native library available to the real Hadoop
 * client with zero manual setup.
 *
 * <p>Run it with {@code ./gradlew -p example run} from the repository root.
 */
public class HadoopNativeExample {

    public static void main(String[] args) throws Exception {
        System.out.println("hadoop.home.dir      = " + System.getProperty("hadoop.home.dir"));
        System.out.println("Shell.getHadoopHome  = " + Shell.getHadoopHome());

        boolean loaded = NativeCodeLoader.isNativeCodeLoaded();
        System.out.println("native-hadoop loaded = " + loaded);

        if (!loaded) {
            System.err.println("FAILURE: native-hadoop library was not loaded");
            System.exit(1);
        }
        System.out.println("SUCCESS: native-hadoop library is loaded");
    }
}
