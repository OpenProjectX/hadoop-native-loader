import net.researchgate.release.ReleaseExtension
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.plugins.signing.Sign

plugins {
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0" // nexus publish/close/release
    id("net.researchgate.release") version "3.1.0"

}

allprojects {
    group = "org.openprojectx.hadoop.native.loader.core"
}


subprojects {
    tasks.register<DependencyReportTask>("allDependencies") {}

    // Apply to every module (safe even if a module doesn't publish)
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    // Configure publishing only when the project has a Java component (Kotlin/JVM typically applies java too).
    // Gradle plugin modules publish themselves via the `java-gradle-plugin` (`gradlePlugin {}`) block, so skip
    // the generic publication there to avoid duplicate publications with the same coordinates.
    // Ensure the artifacts Maven Central requires exist for every Java module
    // (including the gradle-plugin module's own `pluginMaven` publication).
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            withSourcesJar()
            withJavadocJar()
        }

        // Kotlin-only modules can produce "empty-ish" Javadoc; don't fail the build on doclint/errors
        tasks.withType(Javadoc::class.java).configureEach {
            isFailOnError = false
        }
    }

    // Publication wiring is done in afterEvaluate so plugin state is final: a
    // module that applies `java-gradle-plugin` already provides its own
    // `pluginMaven` (+ marker) publications, so we must NOT add a second
    // `mavenJava` with the same coordinates/artifacts (that produced the
    // duplicate-signature / implicit-dependency failures during release).
    afterEvaluate {
        plugins.withId("java") {
            val isGradlePlugin = plugins.hasPlugin("java-gradle-plugin")

            extensions.configure<PublishingExtension>("publishing") {
                if (!isGradlePlugin && publications.findByName("mavenJava") == null) {
                    publications.create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        artifactId = project.name
                    }
                }

                // Apply POM metadata to every Maven publication (mavenJava,
                // pluginMaven and the plugin marker) so Maven Central accepts them.
                publications.withType(MavenPublication::class.java).configureEach {
                    pom {
                        name.set("${project.name} (Hadoop Native Loader)")
                        description.set("Extracts the bundled Hadoop native libraries and puts them on the native library path.")
                        url.set("https://github.com/OpenProjectX/hadoop-native-loader")

                        licenses {
                            license {
                                name.set("Apache License 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0")
                            }
                        }
                        developers {
                            developer {
                                id.set("OpenProjectX")
                                name.set("OpenProjectX")
                                email.set("admin@openprojectx.org")
                            }
                        }
                        scm {
                            url.set("https://github.com/OpenProjectX/hadoop-native-loader")
                            connection.set("scm:git:https://github.com/OpenProjectX/hadoop-native-loader.git")
                            developerConnection.set("scm:git:ssh://git@github.com:OpenProjectX/hadoop-native-loader.git")
                        }
                    }
                }
            }

            // Signing: only configure keys if provided (keeps local dev painless).
            extensions.configure<SigningExtension>("signing") {
                val keyFile = System.getenv("SIGNING_KEY_FILE")
                val keyPass = System.getenv("SIGNING_KEY_PASSWORD")
                if (!keyFile.isNullOrBlank()) {
                    useInMemoryPgpKeys(file(keyFile).readText(), keyPass)
                    sign(extensions.getByType(PublishingExtension::class.java).publications)
                }
            }
        }
    }

    // Break the implicit dependency between signing and publishing tasks: every
    // publish task must run after every sign task in the same project. Without
    // this Gradle 9 fails the build with an "implicit dependency" validation
    // error (a publish task consuming a *.asc produced by a Sign task).
    tasks.withType<AbstractPublishToMaven>().configureEach {
        mustRunAfter(tasks.withType<Sign>())
    }
}


nexusPublishing {

    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(System.getenv("OSSRH_USERNAME"))
            password.set(System.getenv("OSSRH_PASSWORD"))

        }
    }
}

configure<ReleaseExtension> {
    buildTasks.set(listOf("publishToSonatype", "closeAndReleaseSonatypeStagingRepository"))
    versionPropertyFile.set("gradle.properties")
    tagTemplate.set("\$name-\$version")

    with(git) {
        requireBranch.set("master")
    }
}