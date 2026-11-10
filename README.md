# payment-gradle-plugins

Shared Gradle convention plugins for [`payment-app`](https://github.com/team-uts/payment-app) and [`point-app`](https://github.com/team-uts/point-app), extracted from the
`buildSrc/` directory that both repos were carrying as a byte-for-byte copy.

Distributed as a plain Maven repository served by GitHub Pages from this repo's `gh-pages`
branch - no credentials on the consumer side, and no JitPack.

- Repository: `https://team-uts.github.io/payment-gradle-plugins`
- Coordinates: `dev.teamuts.gradle:payment-gradle-plugins:<version>`
- Plugin id: `dev.teamuts.gradle.payment-convention`
- Gradle: 9.5.1 (pinned - see [Gradle version policy](#gradle-version-policy))
- Java toolchain: 21

## What it provides

### The plugin

#### Test Logging
One id, `dev.teamuts.gradle.payment-convention`, which sets up the whole test story for a module:
JUnit Platform, per-test logging, and an aggregated summary box
printed once the last `:test` task of the build finishes.

The summary is driven by a task-completion listener: `BuildLifecyclePlugin` snapshots every task
path ending in `:test` at `taskGraph.whenReady`, and `BuildOperationService` prints
`TestContainer.testResults` when one of those tasks finishes.

> **Known gap:** `TestExtension.setEnabled(false)` would skip the logging, the listener and the
> summary, but `PaymentConventionExtension` exposes nothing, so `paymentConvention { }` is currently
> an empty block and the switch is unreachable from a build script. Surface it on the extension
> before documenting it as a knob.

#### Flyway Migration
Additionally, it supports flyway migration package for the persistence module, which is
profile-aware and reads the `-Dprofile` system property to determine the appropriate configuration.

## Consuming

- [payment-app](https://github.com/team-uts/payment-app)
- [point-app](https://github.com/team-uts/point-app)

### 1. settings.gradle.kts

One repository line. Because the publication includes a plugin **marker** per id, the
`plugins {}` DSL resolves the ids on its own - no `resolutionStrategy` mapping needed:

```kotlin
pluginManagement {
  repositories {
    maven { url = uri("https://team-uts.github.io/payment-gradle-plugins") }
    gradlePluginPortal()
    mavenCentral()
  }
}
```

### 2. gradle/libs.versions.toml

```toml
[versions]
payment-convention = "[VERSION]"

[plugins]
teamuts-paymentConvention = { id = "dev.teamuts.gradle.payment-convention", version.ref = "payment-convention" }
```

### 3. build.gradle.kts

Keep the Mockito dependency in your own catalog - the plugin creates the `mockitoAgent`
configuration but never populates it:

```kotlin
plugins {
  java
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependencyManagement)
  alias(libs.plugins.spotless)
  alias(libs.plugins.teamuts.paymentConvention)
}

val projectLibs = libs

subprojects {
  apply(plugin = "dev.teamuts.gradle.payment-convention")

  dependencies {
    testImplementation(projectLibs.mockito)
    "mockitoAgent"(projectLibs.mockito) { isTransitive = false }
  }

  // or
  val mockitoAgent = configurations.maybeCreate("mockitoAgent")
  dependencies {
    testImplementation(projectLibs.mockito)
    mockitoAgent(projectLibs.mockito) { isTransitive = false }
  }
}
```
If no `mockitoAgent` dependency is
declared the agent is simply not attached - `MockitoAgentProvider` contributes no argument for an
empty collection, so there is no broken bare `-javaagent:` flag to kill the test JVM.

Classes such as `FlywayManager` are available to every module's build script, because a plugin
applied in the root build script joins the buildscript classpath that subprojects inherit:

```kotlin
// persistence/build.gradle.kts
import dev.teamuts.gradle.flyway.FlywayManager

flyway {
  val flywayManager = FlywayManager.init()

  baselineDescription = "Start Flyway Migration!"
  baselineOnMigrate = true
  locations = flywayManager.getLocations()
  configFiles = flywayManager.getConfigFiles()
  user = flywayManager.getUser()
  password = flywayManager.getPassword()
}
```

### 4. Delete `buildSrc/`

Once the above resolves, `buildSrc/` in the consumer repo is dead weight - remove the directory
and its `build.gradle.kts` / `settings.gradle.kts`, and re-point the imports left in the app's
build scripts at the `dev.teamuts.gradle.*` packages.

## Gradle version policy

`BuildLifecyclePlugin` uses two Gradle internals, `BuildEventListenerRegistryInternal` and
`DefaultGradle`, which carry no cross-version compatibility guarantee. The wrapper in this repo
is therefore pinned to **the same Gradle version the consumer apps run (9.5.1)**. Bump the
consumers first, then this repo - never the other way round.

The same reasoning applies to the Java toolchain: `gradle/libs.versions.toml` pins `java = "21"`
to match the consumer apps.

## Building this repo

```bash
./gradlew build   # compileKotlin, validatePlugins, spotlessCheck, test
./gradlew test    # the TestKit functional tests alone
```

### CI

`.github/workflows/ci.yml` runs on every push to `main`: the functional tests first (fast,
attributable failure), then `build`, then `publishAllPublicationsToPagesRepository
-Pversion=ci-snapshot`. That last step exists because a green `build` does not prove the release
publication resolves; without `-PpagesRepoDir` it lands in `build/repo` and is thrown away.

## Releasing

### One-time setup

Already done for this repo - kept here for reference, and for the next repo that copies the setup.
The Maven repository lives on an orphan `gh-pages` branch:

```bash
git switch --orphan gh-pages
touch .nojekyll                       # stop Jekyll from touching the artifact tree
git add .nojekyll
git commit -m "Initialise Maven repository"
git push -u origin gh-pages
git switch main
```

Then `Settings` → `Pages` → Source: *Deploy from a branch*, Branch: `gh-pages` - `/ (root)`.

**The repo must be public**, or GitHub Pages will not serve it on a free plan.

### Each release

`Actions` → `Release` → `Run workflow` → version without the `v` (e.g. `0.2.0`). The workflow:

1. validates the version is `MAJOR.MINOR.PATCH` and that `v<version>` is not already tagged,
2. runs `build` (compile, `validatePlugins`, `spotlessCheck`, tests) with `-Pversion=<version>`,
3. checks out `gh-pages` into `maven-repo/`, refuses to overwrite an already-published version, and
   publishes into that checkout with `-PpagesRepoDir` so Gradle **merges** the new version into the
   existing `maven-metadata.xml` rather than replacing it,
4. pushes `gh-pages`, tags `v<version>`, creates the GitHub Release with generated notes,
5. polls Pages for up to 5 minutes until the new `.pom` is actually being served.

The Maven version and the git tag are decoupled: tag `v0.1.0`, catalog `0.1.0`.

**Never overwrite a published version** - consumers cache artifacts by coordinate. The workflow
enforces this, but the `gh-pages` branch is writable by hand too.
