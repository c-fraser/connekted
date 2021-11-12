/*
Copyright 2021 c-fraser

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package io.github.cfraser.connekted

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.typesafe.config.Config as TypesafeConfig
import com.typesafe.config.ConfigFactory
import io.github.cfraser.connekted.common.Configs
import java.nio.file.Paths
import mu.KotlinLogging
import org.slf4j.LoggerFactory

/**
 * The [Config] interface defines functions that provide access to configuration values by path. A
 * path is a dot-separated expression such as `foo.bar.baz`.
 */
interface Config {

  /**
   * Return the [String] value at the requested path. If the path is non-existent, the value is
   * blank, or the value cannot be converted to [String] then `null` is returned.
   *
   * @param path the path to query
   * @return the string value or `null`
   */
  fun getStringOrNull(path: String): String?

  /**
   * Return the [List] of [String] values at the requested path. If the path is non-existent, the
   * value is blank, or the value cannot be converted to a [List] of [String] instances then `null`
   * is returned.
   *
   * @param path the path to query
   * @return the list of string values or `null`
   */
  fun getStringListOrNull(path: String): List<String>?

  /**
   * Return the [Boolean] value at the requested path. If the path is non-existent, the value is
   * blank, or the value cannot be converted to [Boolean] then `null` is returned.
   *
   * @param path the path to query
   * @return the boolean value or `null`
   */
  fun getBooleanOrNull(path: String): Boolean?

  /**
   * Return the [Int] value at the requested path. If the path is non-existent, the value is blank,
   * or the value cannot be converted to [Int] then `null` is returned.
   *
   * @param path the path to query
   * @return the int value or `null`
   */
  fun getIntOrNull(path: String): Int?

  /**
   * Return the [Long] value at the requested path. If the path is non-existent, the value is blank,
   * or the value cannot be converted to [Long] then `null` is returned.
   *
   * @param path the path to query
   * @return the long value or `null`
   */
  fun getLongOrNull(path: String): Long?

  companion object {

    /**
     * Initialize the [Config] instance for this process.
     *
     * @return the initialized [Config]
     */
    internal fun initialize(): Config = ConfigImpl
  }
}

/**
 * [ConfigImpl] is the internal implementation of [Config] which uses [TypesafeConfig] as the
 * mechanism to load and get values from the `connekted` configuration file.
 */
private object ConfigImpl : Config {

  private val logger = KotlinLogging.logger {}

  private val typesafeConfig: TypesafeConfig =
      ConfigFactory.parseFile(
              Paths.get("${Configs.configMountPath}/${Configs.configFile}").toFile())
          .withFallback(ConfigFactory.load())

  init {
    when (val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)) {
      is Logger -> {
        logger.level =
            getStringOrNull(Configs.loggingLevelPath)?.let { Level.toLevel(it) } ?: Level.INFO
      }
      else -> error("failed to configure logger")
    }
  }

  override fun getStringOrNull(path: String): String? = getOrNull(path, typesafeConfig::getString)

  override fun getStringListOrNull(path: String): List<String>? =
      getOrNull(path, typesafeConfig::getStringList)

  override fun getBooleanOrNull(path: String): Boolean? =
      getOrNull(path, typesafeConfig::getBoolean)

  override fun getIntOrNull(path: String): Int? = getOrNull(path, typesafeConfig::getInt)

  override fun getLongOrNull(path: String): Long? = getOrNull(path, typesafeConfig::getLong)

  private fun <T> getOrNull(path: String, get: (String) -> T): T? =
      path.takeIf { typesafeConfig.hasPath(it) }?.let {
        kotlin
            .runCatching { get(it) }
            .onFailure { logger.error(it) { "failed to get $it" } }
            .getOrNull()
      }
}
