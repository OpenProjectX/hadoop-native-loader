plugins {
    id("buildsrc.convention.kotlin-jvm")
}


dependencies {
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)

    // Real Hadoop client, used to prove its NativeCodeLoader/Shell pick up the
    // artifacts this library prepares. Drop Hadoop's reload4j SLF4J binding so
    // Log4j2 is the single binding on the test classpath.
    testImplementation(libs.hadoopCommon) {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
    }

    // Log4j2 as the test logging backend (Hadoop logs via SLF4J 1.7.x).
    testImplementation(libs.bundles.log4j2)
}
