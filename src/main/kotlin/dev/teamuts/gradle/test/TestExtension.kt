package dev.teamuts.gradle.test

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject

/**
 * The plugin creates the `mockitoAgent` configuration but deliberately leaves it empty, so the
 * Mockito version stays in the consumer's version catalog:
 * ```
 * dependencies {
 *   mockitoAgent(libs.mockito) { isTransitive = false }
 * }
 * ```
 * With no dependency declared the agent is simply not attached.
 */
abstract class TestExtension {
  private val project: Project
  private val mockitoAgent: Configuration
  private var enabled: Boolean = true

  @Inject
  constructor(project: Project) {
    this.project = project
    // because of afterEvaluate, the configuration is created but empty until the consumer adds a dependency
    this.mockitoAgent = project.configurations.maybeCreate(MOCKITO_AGENT_CONFIGURATION)
  }

  companion object {
    private const val MOCKITO_AGENT_CONFIGURATION = "mockitoAgent"
  }

  fun setEnabled(enabled: Boolean) {
    this.enabled = enabled
  }

  fun configure() {
    project.tasks.withType<Test>().configureEach {
      useJUnitPlatform()

      // https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3
      jvmArgumentProviders.add(
        project.objects.newInstance<MockitoAgentProvider>().apply { agentJar.from(mockitoAgent) }
      )

      jvmArgs("-Xshare:off") // deactivate Class Data Sharing(CDS)

      if (enabled.not()) {
        return@configureEach
      }

      testLogging {
        events = setOf(TestLogEvent.FAILED, TestLogEvent.SKIPPED, TestLogEvent.STANDARD_ERROR)

        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
      }

      addTestListener(
        object : TestListener {
          override fun beforeSuite(desc: TestDescriptor) {}

          // handling after all test finished
          override fun afterSuite(desc: TestDescriptor, result: TestResult) {
            if (desc.parent != null) return

            TestContainer.testResults =
              TestSummary(projectName = project.name, taskName = name, result = result)
          }

          override fun beforeTest(desc: TestDescriptor) {}

          // handling after each test finished
          override fun afterTest(desc: TestDescriptor, result: TestResult) {
            TestLoggingUtils.printEachResult(desc, result)
          }
        }
      )
    }

    if (enabled) {
      project.pluginManager.apply(BuildLifecyclePlugin::class.java)
    }
  }
}
