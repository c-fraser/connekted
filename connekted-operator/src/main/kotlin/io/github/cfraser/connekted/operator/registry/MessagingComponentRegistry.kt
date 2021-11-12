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

import java.util.concurrent.ConcurrentHashMap
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * The [MessagingComponentRegistry] stores the *messaging application* name corresponding to each
 * *messaging component*. The *messaging application* to *messaging component(s)* mapping allows for
 * the location(s) of a *messaging component* to be quickly determined.
 */
internal interface MessagingComponentRegistry {

  /**
   * Retrieve the *messaging application* names for the *messaging component* [name].
   *
   * @param name the name of the *messaging component* to get *messaging application* names for
   * @return the hostnames
   */
  fun messagingApplicationNames(name: String): Set<String>

  /**
   * Update the [MessagingComponentRegistry] with the *messaging component(s)* running on the
   * *messaging application* corresponding to [name].
   *
   * @param name the name of the *messaging application* to get *messaging component(s)* names for,
   * then update the registry
   */
  fun updateMessagingComponents(name: String)

  /**
   * Remove the *messaging application* with [name] from the [MessagingComponentRegistry].
   *
   * @param name the name of the *messaging application* to remove
   */
  fun removeMessagingApplication(name: String)
}

private val logger = KotlinLogging.logger {}

/**
 * The [MessagingComponentRegistry] implementation that uses the [messagingApplicationRegistry] as
 * the source to retrieve the *messaging application* names for each *messaging component*.
 *
 * @property messagingApplicationRegistry the [MessagingApplicationRegistry] to use to get
 * *messaging component* names from *messaging applications*
 * @constructor Upon initialization, populate the [messagingComponentsMap] by retrieving *messaging
 * components* for each messaging application in the [messagingApplicationRegistry]
 */
@Component
internal class MessagingComponentRegistryImpl(
    private val messagingApplicationRegistry: MessagingApplicationRegistry
) : MessagingComponentRegistry {

  private val messagingComponentsMap: MutableMap<String, Set<String>> = ConcurrentHashMap()

  init {
    messagingApplicationRegistry.getAll().forEach {
      updateMessagingComponents(checkNotNull(it.name))
    }
  }

  override fun messagingApplicationNames(name: String): Set<String> {
    return messagingComponentsMap[name]?.also { names ->
      logger.debug { "messaging applications $names contain messaging component $name" }
    }
        ?: emptySet<String>().also {
          logger.debug { "no messaging applications have messaging component $name" }
        }
  }

  override fun updateMessagingComponents(name: String) {
    logger.debug { "updating messaging components registry $messagingComponentsMap" }

    val messagingComponents =
        messagingApplicationRegistry[name]
            ?.messagingComponents
            ?.mapNotNull { messagingComponentData -> messagingComponentData.name }
            ?.toSet()
            ?: emptySet()

    for (messagingComponent in messagingComponents) messagingComponentsMap.compute(
        messagingComponent) { _, messagingApplications ->
      if (messagingApplications == null) setOf(name) else messagingApplications + name
    }

    logger.debug { "updated messaging components registry $messagingComponentsMap" }
  }

  override fun removeMessagingApplication(name: String) {
    logger.debug { "removing messaging application $name from the registry" }

    for ((messagingComponent, _) in
        messagingComponentsMap.filterValues { messagingApplications ->
          name in messagingApplications
        }) {
      messagingComponentsMap.computeIfPresent(messagingComponent) { _, messagingApplications ->
        (messagingApplications - name).takeUnless { it.isEmpty() }
      }
    }

    logger.debug {
      "removed messaging application $name from the registry ($messagingComponentsMap)"
    }
  }
}
