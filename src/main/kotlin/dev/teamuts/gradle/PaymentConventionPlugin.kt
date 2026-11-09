package dev.teamuts.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The test setup shared by payment-app and point-app: JUnit Platform, the Mockito agent, per-test
 * logging, and an aggregated summary printed once the last test task of the build finishes.
 *
 * Apply it to every module that has tests:
 * ```
 * subprojects {
 *   apply(plugin = "dev.teamuts.gradle.payment-convention")
 * }
 * ```
 */
class PaymentConventionPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.extensions.create(
      /* name = */ PaymentConventionExtension.EXTENSION,
      /* type = */ PaymentConventionExtension::class.java,
      /* ...constructionArguments = */ project,
    )
  }
}
