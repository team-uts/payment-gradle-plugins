# payment-gradle-plugins

Shared Gradle convention plugins for `payment-app` and `point-app`, extracted from the
`buildSrc/` directory that both repos were carrying as a byte-for-byte copy.

Distributed as a plain Maven repository served by GitHub Pages from this repo's `gh-pages`
branch — no credentials on the consumer side, and no JitPack.

## What it provides

### Plugins

One id, `dev.teamuts.gradle.payment-convention`: the whole test setup — JUnit Platform, the Mockito
agent, `-Xshare:off`, per-test logging, and the aggregated summary box printed once the last
`:test` task of the build finishes.

Internally that is two classes, because the scopes differ: `PaymentConventionPlugin` configures
`Test` tasks per project, while `BuildLifecyclePlugin` registers the build-scoped shared service
behind the summary (`registerIfAbsent`, so once per build however many projects apply it).
`BuildLifecyclePlugin` is applied for you and is deliberately *not* published as its own id —
nothing needs the summary without the test conventions. Note that a class is only on a build
script's compile classpath if the plugin is actually applied to that project, so adding the id
back is the only zero-friction way to use it standalone.

### Classes, for use from build scripts

Everything lives under `dev.teamuts.gradle`, so the `buildSrc` imports in the consumer apps need
updating when you migrate (`task.foo` → `dev.teamuts.gradle.task.foo`):

- `dev.teamuts.gradle.flyway.FlywayManager` — profile-aware Flyway locations, config files
  and credentials.
- `dev.teamuts.gradle.flyway.BuildProfile` — reads `-Dprofile=local|alpha`.
- `dev.teamuts.gradle.extensions.pluginId()` — `Provider<PluginDependency>.pluginId()`, for
  `apply { plugin(libs.plugins.spotless.pluginId()) }`.
- `dev.teamuts.gradle.test.TestSummary` / `TestLoggingUtils` / `TestContainer` /
  `MockitoAgentProvider`.

## Consuming

### 1. settings.gradle.kts

One repository line. Because the publication includes a plugin **marker** per id, the
`plugins {}` DSL resolves the ids on its own — no `resolutionStrategy` mapping needed:

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
teamuts-gradle-plugins = "0.1.0"

[plugins]
teamuts-testConvention = { id = "dev.teamuts.gradle.test-convention", version.ref = "teamuts-gradle-plugins" }
```

### 3. build.gradle.kts

Declare the plugin once in the root with `apply false`, then apply it to the modules that have
tests. Keep the Mockito dependency in your own catalog — the plugin creates the `mockitoAgent`
configuration but never populates it:

```kotlin
plugins {
  java
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependencyManagement)
  alias(libs.plugins.spotless)
  alias(libs.plugins.teamuts.testConvention) apply false
}

subprojects {
  apply(plugin = "dev.teamuts.gradle.test-convention")

  dependencies {
    testImplementation(libs.mockito)
    "mockitoAgent"(libs.mockito) { isTransitive = false }
  }
}
```

That replaces the ~70-line `tasks.withType<Test> { ... }` block, the
`configurations.create("mockitoAgent")` line and the
`pluginManager.apply(BuildLifecyclePlugin::class.java)` call. If no `mockitoAgent` dependency is
declared the agent is simply not attached — no broken `-javaagent:` flag.

Classes such as `FlywayManager` are available to every module's build script, because a plugin
applied in the root build script joins the buildscript classpath that subprojects inherit:

```kotlin
// persistence/build.gradle.kts
import dev.teamuts.gradle.flyway.FlywayManager

flyway {
  val flywayManager = FlywayManager.init()
  locations = flywayManager.getLocations()
  configFiles = flywayManager.getConfigFiles()
  // ...
}
```

### 4. Delete `buildSrc/`

Once the above resolves, `buildSrc/` in the consumer repo is dead weight — remove the directory
and its `build.gradle.kts` / `settings.gradle.kts`, and re-point the imports left in the app's
build scripts at the `dev.teamuts.gradle.*` packages.

## Gradle version policy

`BuildLifecyclePlugin` uses two Gradle internals, `BuildEventListenerRegistryInternal` and
`DefaultGradle`, which carry no cross-version compatibility guarantee. The wrapper in this repo
is therefore pinned to **the same Gradle version the consumer apps run (9.5.1)**. Bump the
consumers first, then this repo — never the other way round.

## Tests

`src/test` holds Gradle TestKit tests that apply `dev.teamuts.gradle.test-convention` **by id**
to a throwaway build and assert on the result — per-test logging, the summary box, the Mockito
agent being attached only when `mockitoAgent` has a dependency, and the helper classes being
importable from a consumer build script under their published package names.

```bash
./gradlew test       # also runs as part of `check` / `build`
```

They exist because nothing else loads the plugin class: a stale `implementationClass` or a
package rename compiles, passes `validatePlugins`, publishes cleanly, and then fails in the
consumer with `ClassNotFoundException`. Verified by reverting `implementationClass` to a stale
value — `validatePlugins` still passed, all four tests failed.

## Releasing

### One-time setup

The Maven repository lives on an orphan `gh-pages` branch. Create it once:

```bash
git switch --orphan gh-pages
touch .nojekyll                       # stop Jekyll from touching the artifact tree
git add .nojekyll
git commit -m "Initialise Maven repository"
git push -u origin gh-pages
git switch main
```

Then `Settings` → `Pages` → Source: *Deploy from a branch*, Branch: `gh-pages` / `/ (root)`.

**The repo must be public**, or GitHub Pages will not serve it on a free plan.

### Each release

`Actions` → `Release` → `Run workflow` → version without the `v` (e.g. `0.1.0`). The workflow:

1. validates the version and that `v<version>` is not already tagged,
2. runs `build` (compile, `validatePlugins`, `spotlessCheck`),
3. checks out `gh-pages`, refuses to overwrite an already-published version, and publishes into
   that checkout so Gradle **merges** the new version into the existing `maven-metadata.xml`
   rather than replacing it,
4. pushes `gh-pages`, tags `v<version>`, creates the GitHub Release,
5. polls Pages until the new `.pom` is actually being served.

The Maven version and the git tag are decoupled: tag `v0.1.0`, catalog `0.1.0`.

Never overwrite a published version — consumers cache artifacts by coordinate.

