package dev.teamuts.gradle.flyway

class FlywayManager(private val activeProfile: BuildProfile) {
  companion object {
    private const val MIGRATION_DIR = "migration"
    private const val SEED_DIR = "seed"
    private const val MIGRATION_PATH = "src/main/resources/db"
    private const val USER = "user"
    private const val DB_PASSWORD = "password"

    fun init(): FlywayManager {
      return FlywayManager(activeProfile = BuildProfile.init())
    }
  }

  fun getConfigFiles(): Array<String> {
    return listOf("flyway-${activeProfile.getProfileName()}.conf")
      .map { "${directoryPrefix()}/$it" }
      .toTypedArray()
  }

  fun getLocations(): Array<String> {
    // Apply Seed data in dev environment only
    val locationDirs =
      if (activeProfile.isDev()) listOf(MIGRATION_DIR, SEED_DIR) else listOf(MIGRATION_DIR)

    return locationDirs
      .map {
        "filesystem:${directoryPrefix()}/$it"
      }
      .toTypedArray()
  }

  fun getUser(): String? {
    return System.getProperty(USER)
  }

  fun getPassword(): String? {
    return System.getProperty(DB_PASSWORD)
  }

  private fun directoryPrefix(): String = MIGRATION_PATH
}
