package dev.teamuts.gradle.test

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

abstract class MockitoAgentProvider : CommandLineArgumentProvider {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val agentJar: ConfigurableFileCollection

  // An empty collection would produce a bare `-javaagent:` and kill the test JVM, so modules that
  // declare no mockitoAgent dependency contribute no argument at all.
  override fun asArguments(): Iterable<String> =
    if (agentJar.isEmpty) emptyList() else listOf("-javaagent:${agentJar.asPath}")
}
