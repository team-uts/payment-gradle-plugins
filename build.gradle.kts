plugins {
  /**
   * `kotlin-dsl` = kotlin("jvm") + `java-gradle-plugin`, compiled against the Gradle API of the
   * wrapper in this repo. Keep that wrapper on the same version the consumer apps run, because
   * `BuildLifecyclePlugin` uses Gradle internals that carry no cross-version guarantee.
   */
  `kotlin-dsl`
  `java-test-fixtures`
  `maven-publish`
  alias(libs.plugins.spotless)
}

group = "dev.teamuts.gradle"

description = "Shared Gradle convention plugins for payment-app and point-app"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(libs.versions.java.get())
  }
}

/**
 * A single entry point. `BuildLifecyclePlugin` stays a separate class. It registers a build-scoped
 * shared service, where this one configures per-project Test tasks. But it is an implementation
 * detail that PaymentConventionPlugin applies, not a published id. Give it its own id only once
 * something wants the summary without the test conventions.
 */
gradlePlugin {
  plugins {
    create("paymentGradleConvention") {
      id = "dev.teamuts.gradle.payment-convention"
      implementationClass = "dev.teamuts.gradle.PaymentConventionPlugin"
      displayName = "gradle convention plugin for payment app projects"
      description =
        "JUnit Platform, the Mockito agent, per-test logging and an aggregated test summary"
    }
  }
}

/**
 * `validatePlugins` never loads the plugin class, so a stale `implementationClass` or a renamed
 * package compiles, passes `check`, publishes, and only blows up in a consumer with
 * `ClassNotFoundException`. The tests in `src/test` apply the real plugin id to throwaway builds
 * through Gradle TestKit, which is the cheapest place to catch that. No extra source set:
 * `java-gradle-plugin` already puts the plugin-under-test metadata that `withPluginClasspath()`
 * reads on the default `test` source set.
 */
dependencies {
  testImplementation(platform(libs.junit.bom))
  testImplementation(gradleTestKit())
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
  useJUnitPlatform()

  // Keeps the throwaway builds on the same JUnit as this repo's catalog.
  systemProperty("junit.version", libs.versions.junit.get())
}

tasks.withType<Jar>().configureEach {
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
}

// https://docs.gradle.org/current/userguide/java_testing.html?utm_source=chatgpt.com#sec:publishing_test_fixtures
(components["java"] as AdhocComponentWithVariants).also { javaComponent ->
  // test-fixtures publication skipped.
  listOf(
      "testFixturesApiElements",
      "testFixturesRuntimeElements",
    )
    .forEach { variant ->
      javaComponent.withVariantsFromConfiguration(configurations[variant]) {
        skip()
      }
    }
}

/**
 * Releases are served as a plain Maven repository from the `gh-pages` branch, so consumers need
 * nothing but a `maven { url = ... }` line — `java-gradle-plugin` publishes a marker per plugin id
 * alongside the jar, which is what lets the `plugins {}` DSL resolve the ids by itself.
 */
val pagesRepoDir =
  providers
    .gradleProperty("pagesRepoDir")
    .map { file(it) }
    .orElse(layout.buildDirectory.dir("repo").map { it.asFile })

publishing {
  repositories {
    maven {
      name = "pages"
      url = uri(pagesRepoDir.get())
    }
  }
}

spotless {
  kotlin {
    target("src/**/*.kt")
    targetExclude("**/build/**")

    ktfmt().googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("**/build/**")

    ktfmt().googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }
}
