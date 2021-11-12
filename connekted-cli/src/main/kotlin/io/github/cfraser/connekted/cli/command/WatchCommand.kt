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
package io.github.cfraser.connekted.cli.command

import com.beust.klaxon.JsonReader
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import io.fabric8.kubernetes.client.KubernetesClient
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * [WatchCommand] is a [CliktCommand] to watch the logs for running messaging application(s).
 *
 * @property kubernetesClient the [KubernetesClient] to use to watch the messaging application pods
 */
@Singleton
@OptIn(ExperimentalTime::class)
@Suppress("unused")
internal class WatchCommand(
    private val kubernetesClient: KubernetesClient,
    private val terminal: Terminal
) : CliktCommand(name = "watch", help = "Watch the logs for running messaging application(s)") {

  private val names: List<String> by
      argument(help = "the name(s) of the messaging application(s) to watch").multiple()

  private val seconds: Duration by
      option(
              "-s",
              "--seconds",
              help =
                  "(optional) - the number of seconds to watch the messaging application(s) logs for")
          .long()
          .convert { Duration.seconds(it) }
          .default(Duration.INFINITE)

  override fun run() {
    val watches =
        mutableListOf<Job>().apply {
          // Cancel each log watching coroutine when a user interrupt occurs
          Runtime.getRuntime()
              .addShutdownHook(thread(start = false) { forEach { watch -> watch.cancel() } })
        }

    runBlocking(Dispatchers.IO) {
      val colors =
          listOf(
              TextColors.green,
              TextColors.blue,
              TextColors.yellow,
              TextColors.magenta,
              TextColors.cyan,
              TextColors.gray)
      val colorMap =
          if (names.size == 1 || names.size > colors.size)
              names.associateWith { colors[Random.nextInt().absoluteValue % colors.size] }
          else names.mapIndexed { i, name -> name to colors[i] }.toMap()

      for (name in names) watches +=
          launch {
            val deployment =
                requireNotNull(kubernetesClient.apps().deployments().withName(name)) {
                  "Failed to find deployment for messaging application $name"
                }
            @Suppress("BlockingMethodInNonBlockingContext")
            deployment.waitUntilReady(2, TimeUnit.MINUTES)

            ensureActive()

            val color = colorMap[name] ?: TextColors.white
            val style = TextStyles.bold + TextStyles.italic + color

            // Watch log until specified duration is exceeded or process shutdown is initiated
            deployment.watchLog().use { logWatch ->
              withTimeout(seconds) {
                JsonReader(InputStreamReader(logWatch.output).buffered()).use { reader ->
                  while (true) {
                    try {
                      val message = runInterruptible {
                        reader.beginObject {
                          var timestamp: String? = null
                          var message: String? = null
                          var loggerName: String? = null
                          var level: String? = null
                          while (reader.hasNext()) {
                            when (reader.runCatching { nextName() }.getOrNull()) {
                              "@timestamp" -> timestamp = reader.nextString()
                              "message" -> message = reader.nextString()
                              "logger_name" -> loggerName = reader.nextString()
                              "level" -> level = reader.nextString()
                            }
                          }
                          "${style(name)} - $timestamp $level $loggerName $message"
                        }
                      }

                      terminal.println(message)
                    } catch (_: InterruptedIOException) {}
                    // Force the coroutine to relinquish the thread, this makes it so the log
                    // statements are interleaved, also checks for cancellation
                    yield()
                  }
                }
              }
            }
          }
    }
  }
}
