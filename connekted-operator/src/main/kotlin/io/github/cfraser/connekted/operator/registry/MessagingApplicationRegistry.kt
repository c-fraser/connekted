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
package io.github.cfraser.connekted.operator.registry

import com.github.michaelbull.retry.policy.constantDelay
import com.github.michaelbull.retry.policy.limitAttempts
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.common.MessagingApplicationData
import io.github.cfraser.connekted.k8s.client.MessagingApplicationClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * The [MessagingApplicationRegistry] stores data representing the state of currently running
 * *messaging applications*.
 */
internal interface MessagingApplicationRegistry {

  /**
   * Register the *messaging application* with the [name] in the [MessagingApplicationRegistry].
   *
   * Registered *messaging applications* are periodically queried so that the
   * [MessagingApplicationRegistry] contains the *current* data.
   *
   * @param name the name of the messaging application to register
   */
  fun register(name: String)

  /**
   * Get the [MessagingApplicationData] for the *messaging application* corresponding to the [name].
   *
   * @param name the name of the messaging application to get data for
   * @return the [MessagingApplicationData] or `null` if no messaging application with the [name]
   * exists in the registry
   */
  operator fun get(name: String): MessagingApplicationData?

  /**
   * Get all of the [MessagingApplicationData] stored in the registry.
   *
   * @return the [Set] of [MessagingApplicationData]
   */
  fun getAll(): Set<MessagingApplicationData>

  /**
   * Update the registry with the *messaging application* corresponding to the [name]. The
   * [MessagingApplicationData] is retrieved by making a request to the *messaging application*.
   *
   * @param name the name of the messaging application to update in the registry
   */
  suspend fun update(name: String)

  /**
   * Remove the *messaging application* corresponding to the [name] from the registry.
   *
   * @param name the name of the messaging application to remove
   */
  fun deregister(name: String)
}

private val logger = KotlinLogging.logger {}

/**
 * The [MessagingApplicationRegistry] implementation that uses the [webClient] and
 * [messagingApplicationClient] to retrieve the [MessagingApplicationData] for each *messaging
 * application*.
 *
 * @property messagingComponentRegistry the [MessagingComponentRegistry] to update as *messaging
 * application* state changes
 * @property webClient the [WebClient] to use to get data from messaging applications
 * @constructor Upon initialization, populate the [messagingApplicationData] by retrieving data for
 * all known messaging applications
 * @property kubernetesClient the [KubernetesClient] to use to initialize
 * [MessagingApplicationClient]
 */
@Component
internal class MessagingApplicationRegistryImpl(
    @Lazy private val messagingComponentRegistry: MessagingComponentRegistry,
    private val webClient: WebClient,
    private val kubernetesClient: KubernetesClient
) : MessagingApplicationRegistry {

  private val messagingApplicationClient: MessagingApplicationClient =
      MessagingApplicationClient.new(kubernetesClient)

  private val registryUpdateJobs: MutableMap<String, Job> = ConcurrentHashMap()
  private val messagingApplicationData: MutableMap<String, MessagingApplicationData> =
      ConcurrentHashMap()

  init {
    for (messagingApplication in messagingApplicationClient.list().items) register(
        messagingApplication.metadata.name)
  }

  final override fun register(name: String) {
    registryUpdateJobs.computeIfAbsent(name) {
      // Periodically update the registry with the messaging application data
      @OptIn(DelicateCoroutinesApi::class)
      GlobalScope.launch {
        do {
          runCatching {
            kubernetesClient.apps().deployments().withName(name).waitUntilReady(1, TimeUnit.MINUTES)
            retry(limitAttempts(3) + constantDelay(Duration.ofSeconds(5).toMillis())) { update(it) }
          }
              .onFailure { throwable ->
                logger.warn(throwable) { "failed to update data in registry for $it" }
              }

          delay(Duration.ofMinutes(1).toMillis())
        } while (isActive)
      }
    }
  }

  override fun get(name: String): MessagingApplicationData? {
    return messagingApplicationData[name]
  }

  override fun getAll(): Set<MessagingApplicationData> {
    return messagingApplicationData.values.toSet()
  }

  override suspend fun update(name: String) {
    logger.debug { "updating messaging application registry $messagingApplicationData" }

    messagingApplicationData[name] =
        withContext(Dispatchers.IO) {
          webClient
              .get()
              .uri { uriBuilder ->
                uriBuilder
                    .scheme("http")
                    .host(name)
                    .port(Configs.appPort)
                    .pathSegment("data")
                    .path("/")
                    .build()
              }
              .retrieve()
              .awaitBody<MessagingApplicationData>()
              .also { data ->
                logger.debug { "retrieved messaging application data $data from $name" }
              }
        }

    withContext(Dispatchers.Default) { messagingComponentRegistry.updateMessagingComponents(name) }

    logger.debug { "updated messaging application registry $messagingApplicationData" }
  }

  override fun deregister(name: String) {
    logger.debug { "removing messaging application $name from the registry" }
    registryUpdateJobs.remove(name)?.cancel()
    messagingApplicationData.remove(name)
    messagingComponentRegistry.removeMessagingApplication(name)
    logger.debug {
      "removed messaging application $name from the registry ($messagingApplicationData)"
    }
  }
}
