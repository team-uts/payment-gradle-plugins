package dev.teamuts.gradle

import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Applies the plugin the way a consumer does — by id, through the `plugins {}` DSL — to a throwaway
 * build. This is the only check that actually loads the plugin class, so it is what catches a stale
 * `implementationClass` or a renamed package before a release does.
 */
class PaymentGradleConventionPluginFunctionalTest {

  @TempDir lateinit var projectDir: File

  @Test
  @DisplayName("applies by plugin id and reports per-test and aggregated results")
  fun appliesByPluginId() {
    writeFixture()

    val buildResult = run("test")
    val output = buildResult.output

    assertEquals(TaskOutcome.SUCCESS, buildResult.task(":test")?.outcome, output)
    assertTrue(output.contains("[demo.DemoTest] ok() >> result: SUCCESS"), output)
    assertTrue(output.contains("Test summary: 1 tests, 1 succeeded, 0 failed, 0 skipped"), output)
  }

  @Test
  @DisplayName("attaches the Mockito agent when the mockitoAgent configuration is populated")
  fun attachesMockitoAgent() {
    writeFixture(mockitoAgent = true)

    val output = run("test").output

    assertTrue(output.contains("-javaagent:"), output)
    assertTrue(output.contains("mockito-core"), output)
  }

  @Test
  @DisplayName("adds no -javaagent when nothing declares a mockitoAgent dependency")
  fun skipsMockitoAgentWhenUndeclared() {
    writeFixture(mockitoAgent = false)

    val output = run("test").output

    // An empty configuration must contribute no argument at all; a bare `-javaagent:` would
    // kill the test JVM.
    assertTrue(output.contains("JVM-ARGS [-Xshare:off]"), output)
  }

  @Test
  @DisplayName("exposes the helper classes to consumer build scripts under their published names")
  fun exposesHelperClassesToBuildScripts() {
    writeFixture(
      extraBuildScript =
        """
        tasks.register("printHelpers") {
          val configFiles =
            dev.teamuts.gradle.flyway.FlywayManager.init().getConfigFiles().joinToString()
          val profile = dev.teamuts.gradle.flyway.BuildProfile.init().getProfileName()
          doLast {
            println("CONFIG-FILES " + configFiles)
            println("PROFILE " + profile)
          }
        }
        """
          .trimIndent()
    )

    val output = run("printHelpers").output

    assertTrue(output.contains("CONFIG-FILES src/main/resources/db/flyway-none.conf"), output)
    assertTrue(output.contains("PROFILE none"), output)
  }

  private fun run(vararg arguments: String): BuildResult =
    GradleRunner.create()
      .withProjectDir(projectDir)
      .withPluginClasspath()
      .withArguments(*arguments, "--stacktrace")
      .forwardOutput()
      .build()

  private fun writeFixture(mockitoAgent: Boolean = true, extraBuildScript: String = "") {
    val junitVersion = System.getProperty("junit.version")
    val agentDependency =
      if (mockitoAgent) {
        """"mockitoAgent"("org.mockito:mockito-core:$MOCKITO_VERSION") { isTransitive = false }"""
      } else {
        ""
      }

    projectDir
      .resolve("settings.gradle.kts")
      .writeText(
        """
        dependencyResolutionManagement { repositories { mavenCentral() } }

        rootProject.name = "fixture"
        """
          .trimIndent()
      )

    projectDir
      .resolve("build.gradle.kts")
      .writeText(
        """
        plugins {
          java
          id("dev.teamuts.gradle.payment-convention")
        }

        java { toolchain { languageVersion = JavaLanguageVersion.of($TOOLCHAIN) } }

        dependencies {
          testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
          testRuntimeOnly("org.junit.platform:junit-platform-launcher")
          $agentDependency
        }

        $extraBuildScript
        """
          .trimIndent()
      )

    val testSource = projectDir.resolve("src/test/java/demo/DemoTest.java")
    testSource.parentFile.mkdirs()
    testSource.writeText(
      """
      package demo;

      import java.lang.management.ManagementFactory;
      import org.junit.jupiter.api.Test;

      class DemoTest {
        @Test
        void ok() {
          // Surfaced through the convention's STANDARD_ERROR test logging.
          System.err.println(
              "JVM-ARGS "
                  + ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                      .filter(a -> a.startsWith("-javaagent") || a.startsWith("-Xshare"))
                      .toList());
        }
      }
      """
        .trimIndent()
    )
  }

  private companion object {
    const val MOCKITO_VERSION = "5.23.0"
    const val TOOLCHAIN = 21
  }
}
