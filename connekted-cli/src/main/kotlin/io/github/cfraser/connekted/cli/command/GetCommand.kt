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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique
import com.github.ajalt.mordant.rendering.BorderStyle
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.Table
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.cli.client.V0ApiServerClient
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.common.MessagingApplicationData
import io.github.cfraser.connekted.common.MessagingComponentData
import java.net.ServerSocket
import java.net.URI
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.eclipse.microprofile.rest.client.RestClientBuilder

private val logger = KotlinLogging.logger {}

/**
 * [GetCommand] is a [CliktCommand] to get data for messaging application(s) running on a Kubernetes
 * cluster.
 *
 * @property kubernetesClient the [KubernetesClient] to use to get messaging application data
 * @property restClientBuilder the [RestClientBuilder] to use to build [V0ApiServerClient]
 * @property terminal the [Terminal] to use to display output
 */
@Singleton
internal class GetCommand(
    private val kubernetesClient: KubernetesClient,
    private val terminal: Terminal,
    private val restClientBuilder: Lazy<RestClientBuilder>
) : CliktCommand(name = "get", help = "Get messaging application(s) running in Kubernetes") {

  private val names: Set<String> by
      argument(help = "(optional) - the names of the messaging application(s) to get")
          .multiple()
          .unique()

  override fun run() {
    val messagingApplicationData =
        kubernetesClient
            .services()
            .withName(Configs.operatorName)
            .portForward(Configs.appPort, ServerSocket(0).use { it.localPort })
            .use { localPortForward ->
              val v0ApiServerClient =
                  restClientBuilder
                      .value
                      .baseUri(URI("http://localhost:${localPortForward.localPort}"))
                      .build(V0ApiServerClient::class.java)
              names.takeUnless { it.isEmpty() }?.run {
                runBlocking(Dispatchers.IO) {
                  map { name ->
                        async {
                          runCatching { v0ApiServerClient.getMessagingApplication(name) }
                              .onFailure {
                                logger.error(it) {
                                  "Failed to get messaging application data for $name"
                                }
                              }
                              .getOrNull()
                        }
                      }
                      .awaitAll()
                      .mapNotNull { it }
                      .toSet()
                }
              }
                  ?: v0ApiServerClient.getMessagingApplications()
            }

    if (names.isNotEmpty() && names.size != messagingApplicationData.size) {
      val missing = names - messagingApplicationData.map { it.name }
      terminal.warning("Failed to get messaging application data for ${missing.joinToString()}")
    }

    if (messagingApplicationData.isNotEmpty()) terminal.println(messagingApplicationData.asTable())
  }

  companion object {

    /** Initialize a [Table] to display the [Collection] of [MessagingApplicationData]. */
    private fun Set<MessagingApplicationData>.asTable() = table {
      borderStyle = BorderStyle.ASCII_DOUBLE_SECTION_SEPARATOR
      header {
        align = TextAlign.CENTER
        style(TextColors.green, bold = true, italic = true)
        row("Messaging application", "Messaging component(s)")
      }
      column(0) { style = TextColors.brightBlue + TextStyles.italic }
      body {
        forEach { messagingApplicationData ->
          row(
              messagingApplicationData.name,
              messagingApplicationData.messagingComponents?.asTable())
        }
      }
    }

    /** Initialize a [Table] to display the [List] of [MessagingComponentData]. */
    private fun List<MessagingComponentData>.asTable() = table {
      align = TextAlign.LEFT
      borderStyle = BorderStyle.ASCII
      body {
        forEachIndexed { i, messagingComponentData ->
          row(
              table {
                align = TextAlign.LEFT
                borderStyle = BorderStyle.BLANK
                body {
                  row("Name:", messagingComponentData.name)
                  row("Type:", messagingComponentData.type)
                  if (messagingComponentData.type == MessagingComponentData.Type.SENDER ||
                      messagingComponentData.type == MessagingComponentData.Type.SENDING_RECEIVER) {
                    row("Sends to:", messagingComponentData.messagingData?.sendTo)
                    row("Messages sent:", messagingComponentData.messagingData?.sent)
                    row("Send errors:", messagingComponentData.messagingData?.sendErrors)
                  }
                  if (messagingComponentData.type == MessagingComponentData.Type.RECEIVER ||
                      messagingComponentData.type == MessagingComponentData.Type.SENDING_RECEIVER) {
                    row("Messages received:", messagingComponentData.messagingData?.received)
                    row("Receive errors:", messagingComponentData.messagingData?.receiveErrors)
                  }
                }
              }) { borders = if (i == 0) Borders.NONE else Borders.TOP }
        }
      }
    }
  }
}
