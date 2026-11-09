package dev.teamuts.gradle

import dev.teamuts.gradle.test.TestExtension
import org.gradle.api.Project

abstract class PaymentConventionExtension(project: Project) {
  init {
    val testExtension = project.objects.newInstance(TestExtension::class.java, project)
    project.afterEvaluate {
      testExtension.configure()
    }
  }

  companion object {
    const val EXTENSION = "paymentConvention"
  }
}
