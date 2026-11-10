package dev.teamuts.gradle.fixtures

import com.google.common.collect.Maps
import com.hubspot.jinjava.Jinjava
import dev.teamuts.gradle.fixtures.TestGradleVersions.JAVA_TOOLCHAIN
import dev.teamuts.gradle.fixtures.TestGradleVersions.MOCKITO_VERSION
import java.io.File

class TestGradleFixture(private val projectDir: File) {
  private val jinjaEngine: Jinjava = Jinjava()

  private companion object {
    const val DIRECTORY_DELIMITER = "/"
    const val TEMPLATE_ROOT = "${DIRECTORY_DELIMITER}templates"
    const val BUILD_GRADLE = "build.gradle.kts"
    const val SETTINGS_GRADLE = "settings.gradle.kts"
    const val DEMO_TEST = "DemoTest.java"
    val EMPTY_CONTEXT = emptyMap<String, Any>()
  }

  /**
   * @param mockitoAgent whether the fixture declares a `mockitoAgent` dependency. The false case is
   *   load-bearing: an empty configuration must contribute no argument at all, because a bare
   *   `-javaagent:` would kill the test JVM.
   */
  fun write(mockitoAgent: Boolean = true) {
    val buildContext =
      Maps.newHashMap<String, Any>().apply {
        this["javaToolchain"] = JAVA_TOOLCHAIN
        this["junitVersion"] = System.getProperty("junit.version")
        this["mockitoAgent"] = mockitoAgent
        this["mockitoVersion"] = MOCKITO_VERSION
      }

    listOf(
        SETTINGS_GRADLE to EMPTY_CONTEXT,
        BUILD_GRADLE to buildContext,
        "src/test/java/demo/$DEMO_TEST" to EMPTY_CONTEXT,
      )
      .forEach {
        writeText(it.first, it.second)
      }
  }

  private fun writeText(fileName: String, context: Map<String, Any>) {
    projectDir
      .resolve(fileName)
      .apply { this.parentFile?.mkdirs() }
      .writeText(
        jinjaEngine.render(
          /* template = */ template(fileName.split(DIRECTORY_DELIMITER).last()),
          /* bindings = */ context,
        )
      )
  }

  private fun template(name: String): String =
    requireNotNull(javaClass.getResourceAsStream("$TEMPLATE_ROOT/${name}.j2")) {
        "No fixture template at $TEMPLATE_ROOT/$name.j2"
      }
      .bufferedReader()
      .use { it.readText() }
}
