package org.javamodularity.moduleplugin;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.cartesian.CartesianTest;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SuppressWarnings("ConstantConditions")
class ModulePluginSmokeTest {
    private static final Logger LOGGER = Logging.getLogger(ModulePluginSmokeTest.class);
    private static final Pattern SEMANTIC_VERSION = Pattern.compile("(?<major>\\d+)\\.(?<minor>\\d+).(?<patch>\\d+)");

    private List<File> pluginClasspath;

    @SuppressWarnings("unused")
    private enum GradleVersion {
        v8_11,
        v9_0,
        v9_1_0
        ;

        @Override
        public String toString() {
            return  name().substring(1).replaceAll("_", ".");
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    @BeforeEach
    void before() throws IOException {
        pluginClasspath = Resources.readLines(Resources.getResource("plugin-classpath.txt"), Charsets.UTF_8)
                .stream()
                .map(File::new)
                .collect(Collectors.toList());
    }

    @CartesianTest(name = "smokeTest({arguments})")
    void smokeTest(
            @CartesianTest.Values(strings = {
                    "test-project",
                    "test-project-kotlin",
                    "test-project-groovy"
            }) String projectName,
            @CartesianTest.Enum GradleVersion gradleVersion) {
        LOGGER.lifecycle("Executing smokeTest of {} with Gradle {}", projectName, gradleVersion);
        assumeTrue(jdkSupported(gradleVersion));
        assumeTrue(checkKotlinCombination(projectName, gradleVersion));
        
        ensureSettingsFileForGradle9(projectName + "/", gradleVersion.toString());
        
        var result = GradleRunner.create()
                .withProjectDir(new File(projectName + "/"))
                .withPluginClasspath(pluginClasspath)
                .withGradleVersion(gradleVersion.toString())
                .withArguments(buildGradleArgs(gradleVersion.toString(), "clean", "build", "run", "--stacktrace"))
                .forwardOutput()
                .build();

        assertTasksSuccessful(result, "greeter.api", "build");
        assertTasksSuccessful(result, "greeter.provider", "build");
        assertTasksSuccessful(result, "greeter.provider.test", "build");
        assertTasksSuccessful(result, "greeter.provider.testfixture", "build");
        assertTasksSuccessful(result, "greeter.runner", "build", "run");
        assertOutputDoesNotContain(result, "warning: [options] --add-opens has no effect at compile time");
    }

    @CartesianTest(name = "smokeTestRun({arguments})")
    void smokeTestRun(
            @CartesianTest.Values(strings = {
                    "test-project",
                    "test-project-kotlin",
                    "test-project-groovy"
            }) String projectName,
            @CartesianTest.Enum GradleVersion gradleVersion) {
        LOGGER.lifecycle("Executing smokeTestRun of {} with Gradle {}", projectName, gradleVersion);
        assumeTrue(jdkSupported(gradleVersion));
        assumeTrue(checkKotlinCombination(projectName, gradleVersion));
        
        ensureSettingsFileForGradle9(projectName + "/", gradleVersion.toString());
        
        var writer = new StringWriter(256);
        
        var result = GradleRunner.create()
                .withProjectDir(new File(projectName + "/"))
                .withPluginClasspath(pluginClasspath)
                .withGradleVersion(gradleVersion.toString())
                .forwardStdOutput(writer)
                .forwardStdError(writer)
                .withArguments("-q", "clean", ":greeter.runner:run", "--args", "aaa bbb")
                .build();

        assertTasksSuccessful(result, "greeter.runner", "run");

        var lines = writer.toString().lines().collect(Collectors.toList());
        assertEquals("args: [aaa, bbb]", lines.get(0));
        assertEquals("greeter.sender: gradle-modules-plugin", lines.get(1));
        assertEquals("welcome", lines.get(2));
    }

    @CartesianTest(name = "smokeTestJunit5({arguments})")
    void smokeTestJunit5(
            @CartesianTest.Values(strings = {
                    "5.10.5/1.10.5"
            }) String junitVersionPair,
            @CartesianTest.Enum GradleVersion gradleVersion) {
        LOGGER.lifecycle("Executing smokeTestJunit5 with junitVersionPair {} and Gradle {}", junitVersionPair, gradleVersion);
        assumeTrue(jdkSupported(gradleVersion));
        var junitVersionParts = junitVersionPair.split("/");
        final String junitVersion = junitVersionParts[0];
        assumeTrue(checkJUnitCombination(junitVersion, gradleVersion));
        var junitVersionProperty = String.format("-PjUnitVersion=%s", junitVersion);
        var junitPlatformVersionProperty = String.format("-PjUnitPlatformVersion=%s", junitVersionParts[1]);
        
        ensureSettingsFileForGradle9("test-project/", gradleVersion.toString());
        
        var result = GradleRunner.create()
                .withProjectDir(new File("test-project/"))
                .withPluginClasspath(pluginClasspath)
                .withGradleVersion(gradleVersion.toString())
                .withArguments(buildGradleArgs(gradleVersion.toString(), junitVersionProperty, junitPlatformVersionProperty, "clean", "build", "run", "--stacktrace"))
                .forwardOutput()
                .build();

        assertTasksSuccessful(result, "greeter.api", "build");
        assertTasksSuccessful(result, "greeter.provider", "build");
        assertTasksSuccessful(result, "greeter.provider.test", "build");
        assertTasksSuccessful(result, "greeter.runner", "build", "run");
    }





    @CartesianTest(name = "smokeTestDist({arguments})")
    void smokeTestDist(
            @CartesianTest.Values(strings = {
                    "test-project",
                    "test-project-kotlin",
                    "test-project-groovy"
            }) String projectName,
            @CartesianTest.Enum GradleVersion gradleVersion) {
        LOGGER.lifecycle("Executing smokeTestDist of {} with Gradle {}", projectName, gradleVersion);
        assumeTrue(jdkSupported(gradleVersion));
        assumeTrue(checkKotlinCombination(projectName, gradleVersion));
        
        ensureSettingsFileForGradle9(projectName + "/", gradleVersion.toString());
        
        var result = GradleRunner.create()
                .withProjectDir(new File(projectName + "/"))
                .withPluginClasspath(pluginClasspath)
                .withGradleVersion(gradleVersion.toString())
                .withArguments(buildGradleArgs(gradleVersion.toString(), "clean", "build", ":greeter.runner:installDist", "--stacktrace"))
                .forwardOutput()
                .build();

        assertTasksSuccessful(result, "greeter.runner", "installDist");
        Path installDir = Path.of(projectName + "/greeter.runner/build/install/greeter.runner");
        assertTrue(installDir.toFile().exists(), "Install dir was not created");

        Path libDir = installDir.resolve("lib");
        Path patchlibsDir = installDir.resolve("patchlibs");

        assertTrue(libDir.toFile().exists(), "Lib dir was not created");
        assertTrue(patchlibsDir.toFile().exists(), "Patchlib dir was not created");

        Path patchedLib = patchlibsDir.resolve("jsr305-3.0.2.jar");
        assertTrue(patchedLib.toFile().exists(), "Patched lib should be in patchlibs dir");

        var libs = Arrays.stream(libDir.toFile().listFiles())
                .map(File::getName)
                .filter(name -> !name.startsWith("kotlin"))
                .filter(name -> !name.startsWith("groovy"))
                .filter(name -> !name.startsWith("annotations"))
                .collect(Collectors.toList());

        assertFalse(libs.contains("jsr305-3.0.2.jar"), "jsr305-3.0.2.jar should not be in libDir");
        assertEquals(4, libs.size(), "Unexpected number of jars in lib dir (" + libs + ")");

        SmokeTestAppContext ctx = SmokeTestAppContext.ofDefault(installDir.resolve("bin"));
        assertTrue(ctx.getAppOutput("greeter.runner").contains("welcome"));
    }

    @CartesianTest(name = "smokeTestRunDemo({arguments})")
    void smokeTestRunDemo(
            @CartesianTest.Values(strings = {
                    "test-project",
                    "test-project-kotlin",
                    "test-project-groovy"
            }) String projectName,
            @CartesianTest.Enum GradleVersion gradleVersion) {
        LOGGER.lifecycle("Executing smokeTestRunDemo of {} with Gradle {}", projectName, gradleVersion);
        assumeTrue(jdkSupported(gradleVersion));
        assumeTrue(checkKotlinCombination(projectName, gradleVersion));
        
        ensureSettingsFileForGradle9(projectName + "/", gradleVersion.toString());
        
        var result = GradleRunner.create()
                .withProjectDir(new File(projectName + "/"))
                .withPluginClasspath(pluginClasspath)
                .withGradleVersion(gradleVersion.toString())
                .withArguments(buildGradleArgs(gradleVersion.toString(), "clean", "build",
                        ":greeter.javaexec:runDemo1", ":greeter.javaexec:runDemo2", "--info", "--stacktrace"))
                .forwardOutput()
                .build();

        assertTasksSuccessful(result, "greeter.javaexec", "runDemo1", "runDemo2");
        assertFalse(result.getOutput().contains("Using Java lambdas is not supported as task inputs"));
    }

    @CartesianTest(name = "smokeTestRunStartScripts({arguments})")
    void smokeTestRunStartScripts(
            @CartesianTest.Values(strings = {
                    "test-project",
                    "test-project-kotlin",
                    "test-project-groovy"
            }) String projectName,
            @CartesianTest.Enum GradleVersion gradleVersion) {
        LOGGER.lifecycle("Executing smokeTestRunScripts of {} with Gradle {}", projectName, gradleVersion);
        assumeTrue(jdkSupported(gradleVersion));
        assumeTrue(checkKotlinCombination(projectName, gradleVersion));
        
        ensureSettingsFileForGradle9(projectName + "/", gradleVersion.toString());
        
        var result = GradleRunner.create()
                .withProjectDir(new File(projectName + "/"))
                .withPluginClasspath(pluginClasspath)
                .withGradleVersion(gradleVersion.toString())
                .withArguments(buildGradleArgs(gradleVersion.toString(), "clean", ":greeter.startscripts:installDist", "--info", "--stacktrace"))
                .forwardOutput()
                .build();

        assertTasksSuccessful(result, "greeter.startscripts", "installDist");

        String binDir = projectName + "/greeter.startscripts/build/install/demo/bin";
        SmokeTestAppContext ctx = SmokeTestAppContext.ofAliceAndBobAtHome(Path.of(binDir));

        assertEquals("MainDemo: welcome home, Alice and Bob!", ctx.getAppOutput("demo"));
        assertEquals("Demo1: welcome home, Alice and Bob!", ctx.getAppOutput("demo1"));
        assertEquals("Demo2: welcome home, Alice and Bob!", ctx.getAppOutput("demo2"));
    }

    @Test
    void shouldNotCheckInWithCommentedOutVersions() {
        assertEquals(3, GradleVersion.values().length);
    }

    private static void assertTasksSuccessful(BuildResult result, String subprojectName, String... taskNames) {
        for (String taskName : taskNames) {
            SmokeTestHelper.assertTaskSuccessful(result, subprojectName, taskName);
        }
    }

    private static void assertOutputDoesNotContain(BuildResult result, String text) {
        final String output = result.getOutput();
        assertFalse(output.contains(text), "Output should not contain '" + text + "', but was: " + output);
    }

    private static boolean checkKotlinCombination(String projectName, GradleVersion gradleVersion) {
        // All Kotlin projects are supported with Gradle 9.0+
        return true;
    }

    private boolean checkJUnitCombination(final String junitVersion, final GradleVersion gradleVersion) {
        final Matcher m = SEMANTIC_VERSION.matcher(junitVersion);
        assumeTrue(m.matches(), "JUnit version not semantic: " + junitVersion);
        final boolean junitOlderThan5_8_0 = Integer.parseInt(m.group("major")) < 5 ||
                (Integer.parseInt(m.group("major")) == 5 && Integer.parseInt(m.group("minor")) < 8);

        if (junitOlderThan5_8_0) {
            LOGGER.lifecycle("Unsupported JUnit version for Gradle 9+. JUnit: {}: Test skipped", junitVersion);
            return false;
        }
        return true;
    }

    private String[] buildGradleArgs(String gradleVersion, String... additionalArgs) {
        // Gradle 9.0+ doesn't support --settings-file argument in tooling API
        // Since we only support Gradle 9.0+, just return additional args
        return additionalArgs;
    }

    private void ensureSettingsFileForGradle9(String projectPath, String gradleVersion) {
        // All supported Gradle versions (9.0+) need settings file copy
        try {
            Path sourceSettings = Path.of(projectPath, "smoke_test_settings.gradle");
            Path targetSettings = Path.of(projectPath, "settings.gradle");
            Path backupSettings = Path.of(projectPath, "settings.gradle.orig");
            
            if (Files.exists(sourceSettings)) {
                // Backup original settings.gradle if not already backed up
                if (!Files.exists(backupSettings)) {
                    Files.copy(targetSettings, backupSettings, StandardCopyOption.REPLACE_EXISTING);
                }
                // Copy smoke test settings to settings.gradle
                Files.copy(sourceSettings, targetSettings, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to copy settings file for Gradle 9.0+: {}", e.getMessage());
        }
    }

    private static int javaMajorVersion() {
        final String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            // Java 8 and earlier (1.8.0_xxx format) - not supported anymore but keep for completeness
            return Integer.parseInt(version.substring(2, version.indexOf(".", 2)));
        } else {
            // Java 9+ (9.0.1, 11.0.2, 17.0.2 format)
            int dotIndex = version.indexOf(".");
            if (dotIndex == -1) {
                // Handle cases like "17" without dot
                return Integer.parseInt(version);
            }
            return Integer.parseInt(version.substring(0, dotIndex));
        }
    }

    private boolean jdkSupported(final GradleVersion gradleVersion) {
        final int javaMajor = javaMajorVersion();
        
        // All supported Gradle versions (9.0+) require Java 17+
        if (javaMajor < 17) {
            LOGGER.lifecycle("Gradle {} requires Java 17+, but running Java {}: Test skipped", gradleVersion, javaMajor);
            return false;
        }
        
        return true;
    }
}
