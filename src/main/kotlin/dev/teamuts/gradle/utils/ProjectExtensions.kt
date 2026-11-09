package dev.teamuts.gradle.utils

import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency

fun Provider<PluginDependency>.pluginId(): String {
  return this.get().pluginId
}
