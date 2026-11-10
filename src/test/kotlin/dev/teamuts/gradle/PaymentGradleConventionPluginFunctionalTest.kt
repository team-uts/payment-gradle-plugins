package dev.teamuts.gradle

import dev.teamuts.gradle.fixtures.GradleRunnerExecutor
import dev.teamuts.gradle.fixtures.TestGradleFixture
import dev.teamuts.gradle.fixtures.TestGradleVersions.MOCKITO_VERSION
import dev.teamuts.gradle.flyway.FlywayManager
import java.io.File
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Applies the plugin the way a consumer does — by id, through the `plugins {}` DSL — to a throwaway
 * build. This is the only check that actually loads the plugin class, so it is what catches a stale
 * `implementationClass` or a renamed package before a release does.
 */
class PaymentGradleConventionPluginFunctionalTest {

  @TempDir private lateinit var projectDir: File
  private lateinit var testGradleFixture: TestGradleFixture

  @BeforeEach
  fun setup() {
    // The fixture template carries `{{...}}` placeholders, so it is not parseable Kotlin.
    testGradleFixture = TestGradleFixture(projectDir)
  }

  @Test
  fun appliesByPluginId() {
    testGradleFixture.write()

    val buildResult = GradleRunnerExecutor.execute(projectDir = projectDir, "test")
    val output = buildResult.output

    assertEquals(TaskOutcome.SUCCESS, buildResult.task(":test")?.outcome, output)
    assertTrue(output.contains("[demo.DemoTest] ok() >> result: SUCCESS"), output)
    assertTrue(output.contains("Test summary: 1 tests, 1 succeeded, 0 failed, 0 skipped"), output)
  }

  @Test
  fun attachesMockitoAgent() {
    testGradleFixture.write(mockitoAgent = true)

    val output = GradleRunnerExecutor.executeAndReturnOutcome(projectDir = projectDir, "test")

    assertTrue(output.contains("-javaagent:"), output)
    assertTrue(output.contains("mockito-core/${MOCKITO_VERSION}"), output)
  }

  @Test
  fun skipsMockitoAgentWhenUndeclared() {
    testGradleFixture.write(mockitoAgent = false)

    val output = GradleRunnerExecutor.executeAndReturnOutcome(projectDir = projectDir, "test")

    // An empty configuration must contribute no argument at all; a bare `-javaagent:` would
    // kill the test JVM.
    assertTrue(output.contains("JVM-ARGS [-Xshare:off]"), output)
  }

  @Test
  fun exposesHelperClassesToBuildScripts() {
    testGradleFixture.write()

    val output =
      GradleRunnerExecutor.executeAndReturnOutcome(projectDir = projectDir, "printHelpers")

    assertTrue(
      output.contains("CONFIG-FILES >> ${FlywayManager.MIGRATION_PATH}/flyway-none.conf"),
      output,
    )
    assertTrue(output.contains("PROFILE >> none"), output)
  }
}
