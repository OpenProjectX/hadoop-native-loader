plugins {
    id("buildsrc.convention.kotlin-jvm")
}


dependencies {
    // Core extraction/loading logic, exposed transitively to consumers.
    api(project(":core"))

    // Hooks the loader into the JUnit 5 lifecycle. These are compile-time only:
    // the JUnit Platform and Jupiter engine are provided by the consuming
    // test runtime.
    compileOnly(libs.junitPlatformLauncher)
    compileOnly(libs.junitJupiterApi)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)

    // Real Hadoop client, to verify the LauncherSessionListener prepares the
    // native environment before Hadoop's NativeCodeLoader runs. Drop Hadoop's
    // reload4j SLF4J binding so Log4j2 is the single binding on the test
    // classpath.
    testImplementation(libs.hadoopCommon) {
        exclude(group = "org.slf4j", module = "slf4j-reload4j")
    }

    // Log4j2 as the test logging backend (Hadoop logs via SLF4J 1.7.x).
    testImplementation(libs.bundles.log4j2)
}
