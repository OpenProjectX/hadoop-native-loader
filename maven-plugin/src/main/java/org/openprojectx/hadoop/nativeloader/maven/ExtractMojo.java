package org.openprojectx.hadoop.nativeloader.maven;

import java.io.File;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.openprojectx.hadoop.nativeloader.HadoopNativeExtractor;

/**
 * Extracts the bundled Hadoop native libraries and exposes them to the test
 * JVM, so Hadoop's {@code NativeCodeLoader} can load {@code libhadoop.so} /
 * {@code hadoop.dll} and {@code Shell} can find {@code winutils}.
 *
 * <p>On JDK 9+ the {@code System.loadLibrary} search path is frozen at JVM
 * startup, so the directory has to be on {@code java.library.path} <em>at
 * launch</em>. Surefire/Failsafe fork a JVM and pass {@code ${argLine}} to it,
 * so — the same way the JaCoCo agent works — this goal prepends
 * {@code -Djava.library.path=<bin>} (and {@code -Dhadoop.home.dir=<dir>}) to the
 * {@code argLine} property. Bind it before {@code test} (it defaults to the
 * {@code initialize} phase) and the test JVM picks the libraries up with no
 * further configuration.
 *
 * <p>If you set Surefire's {@code argLine} explicitly, use late evaluation so
 * this value is preserved: {@code <argLine>@{argLine} ...your args...</argLine>}.
 */
@Mojo(name = "extract", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class ExtractMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Directory the native artifacts are extracted into; libraries land in its {@code bin} sub-directory. */
    @Parameter(property = "hadoopNativeLoader.outputDirectory",
            defaultValue = "${project.build.directory}/hadoop-native")
    private File outputDirectory;

    /**
     * Project property the JVM arguments are appended to. Defaults to
     * {@code argLine}, which Surefire and Failsafe read automatically.
     */
    @Parameter(property = "hadoopNativeLoader.propertyName", defaultValue = "argLine")
    private String propertyName;

    /** Whether to also set {@code -Dhadoop.home.dir} (for {@code winutils}). */
    @Parameter(property = "hadoopNativeLoader.setHadoopHomeDir", defaultValue = "true")
    private boolean setHadoopHomeDir;

    /** Skip execution. */
    @Parameter(property = "hadoopNativeLoader.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("hadoop-native-loader: skipped");
            return;
        }

        File bin = new File(outputDirectory, "bin");
        List<File> written;
        try {
            written = HadoopNativeExtractor.extract(bin);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("Failed to extract Hadoop native libraries to " + bin, e);
        }
        getLog().info("Extracted " + written.size() + " Hadoop native artifact(s) to " + bin);

        StringBuilder args = new StringBuilder();
        args.append("-Djava.library.path=").append(quote(bin.getAbsolutePath()));
        if (setHadoopHomeDir) {
            args.append(" -Dhadoop.home.dir=").append(quote(outputDirectory.getAbsolutePath()));
        }

        String existing = project.getProperties().getProperty(propertyName);
        String value = (existing == null || existing.isBlank()) ? args.toString() : args + " " + existing;
        project.getProperties().setProperty(propertyName, value);

        // Expose the bin directory for manual wiring (e.g. exec-maven-plugin or LD_LIBRARY_PATH).
        project.getProperties().setProperty("hadoopNativeLoader.binDirectory", bin.getAbsolutePath());

        getLog().info("Set property '" + propertyName + "' to: " + value);
    }

    private static String quote(String path) {
        return path.indexOf(' ') >= 0 ? "\"" + path + "\"" : path;
    }
}
