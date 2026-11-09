package dev.teamuts.gradle.flyway

import java.lang.IllegalArgumentException

class BuildProfile private constructor(private val activeProfile: Profile) {
  companion object {
    private const val PROFILE_KEY = "profile"

    fun init(): BuildProfile {
      val activeProfile =
        System.getProperty(PROFILE_KEY)?.let {
          Profile.of(it)
        } ?: Profile.NONE

      return BuildProfile(activeProfile)
    }
  }

  fun isDev(): Boolean {
    return Profile.DEV_SET.contains(activeProfile)
  }

  fun getProfileName(): String {
    return activeProfile.profileName
  }

  enum class Profile(val profileName: String) {
    NONE("none"),
    LOCAL("local"),
    ALPHA("alpha");

    companion object {
      val DEV_SET = setOf(LOCAL, ALPHA)

      fun of(profileName: String): Profile {
        return entries.find {
          it.profileName == profileName
        } ?: throw IllegalArgumentException("invalid profile name")
      }
    }
  }
}
