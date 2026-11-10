package dev.teamuts.gradle.fixtures

import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

class GradleRunnerExecutor {
  companion object {
    fun execute(projectDir: File, vararg arguments: String = emptyArray()): BuildResult {
      return GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")
        .forwardOutput()
        .build()
    }

    fun executeAndReturnOutcome(projectDir: File, vararg arguments: String = emptyArray()): String {
      return execute(projectDir, *arguments).output
    }
  }
}
