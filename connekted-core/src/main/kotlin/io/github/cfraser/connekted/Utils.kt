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

import io.github.cfraser.connekted.common.Configs
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import java.io.Closeable
import java.time.Duration
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The [Envs] object exposes access to value extracted from the environment. */
internal object Envs {

  /** The messaging application name retrieved from the `CONNEKTED_NAME` environment variable. */
  val name: String by lazy { checkNotNull(System.getenv(Configs.nameEnv)) }

  /** The username retrieved from the `CONNEKTED_USERNAME` environment variable. */
  val username: String by lazy { checkNotNull(System.getenv(Configs.usernameEnv)) }

  /** The password retrieved from the `CONNEKTED_PASSWORD` environment variable. */
  val password: String by lazy { checkNotNull(System.getenv(Configs.passwordEnv)) }

  /** The messaging token retrieved from the `CONNEKTED_MESSAGING_TOKEN` environment variable. */
  val messagingToken: String by lazy { checkNotNull(System.getenv(Configs.messagingTokenEnv)) }
}

/** Close the receiver [Closeable] upon JVM shutdown via [Runtime.addShutdownHook]. */
internal fun Closeable.closeOnShutdown() {
  Runtime.getRuntime()
      .addShutdownHook(
          thread(start = false) {
            runCatching {
              runBlocking { withTimeout(Duration.ofSeconds(30).toMillis()) { close() } }
            }
          })
}

/** The [Metrics] object consolidates utility properties and functions relating to metrics. */
internal object Metrics {

  /**
   * The lazily initialized [MeterRegistry] used by the messaging application and messaging
   * components.
   */
  val meterRegistry: MeterRegistry by lazy {
    PrometheusMeterRegistry(PrometheusConfig.DEFAULT).also { meterRegistry ->
      Closeable { meterRegistry.close() }.apply { closeOnShutdown() }
    }
  }

  /**
   * Return the [io.micrometer.core.instrument.Counter.count] for the metric corresponding to the
   * [metricName] and [tags].
   *
   * @param metricName the name of the metric
   * @param tags the [Tags] on the metric
   */
  suspend fun count(metricName: String, tags: Tags) =
      withContext(Dispatchers.Default) {
        runCatching { meterRegistry.get(metricName).tags(tags).counter() }
            .getOrNull()
            ?.count()
            ?.toInt()
      }
}
