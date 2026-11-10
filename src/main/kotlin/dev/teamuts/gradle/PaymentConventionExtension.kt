package dev.teamuts.gradle

import dev.teamuts.gradle.test.TestLoggingExtension
import org.gradle.api.Project

abstract class PaymentConventionExtension(project: Project) {
  init {
    val testExtension =
      project.extensions.create(
        TestLoggingExtension.NAME,
        TestLoggingExtension::class.java,
        project,
      )

    project.afterEvaluate { testExtension.configure() }
  }

  companion object {
    const val NAME = "paymentConvention"
  }
}
