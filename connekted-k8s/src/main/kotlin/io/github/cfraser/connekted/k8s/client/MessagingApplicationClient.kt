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
package io.github.cfraser.connekted.k8s.client

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.k8s.MessagingApplication
import java.io.Closeable

/**
 * The [MessagingApplicationClient] is used to interact with [MessagingApplication] resources in a
 * Kubernetes cluster.
 */
interface MessagingApplicationClient : Closeable {

  /**
   * Create a [MessagingApplication] or replace an existing [MessagingApplication] with [name].
   *
   * @param name the name of the messaging application
   * @param image the container image of the messaging application
   * @param config the messaging application config
   */
  fun createOrReplace(name: String, image: String, config: String?)

  /**
   * Delete a [MessagingApplication] with [name].
   *
   * @param name the name of the messaging application to delete
   * @return `true` if the deletion completed successfully, otherwise `false`
   */
  fun delete(name: String): Boolean

  /**
   * Return the [MessagingApplication] with [name] or `null` if none exists.
   *
   * @param name the name of the messaging application to get
   * @return the [MessagingApplication] with [name] or `null`
   */
  fun get(name: String): MessagingApplication?

  /**
   * Return the [MessagingApplication.List].
   *
   * @return all the existing [MessagingApplication] resources
   */
  fun list(): MessagingApplication.List

  companion object {

    /**
     * Factory function to initialize a [MessagingApplicationClient].
     *
     * @param kubernetesClient the [KubernetesClient] to use to interact with Kubernetes API
     * @return the [MessagingApplicationClient]
     */
    fun new(kubernetesClient: KubernetesClient): MessagingApplicationClient =
        MessagingApplicationClientImpl(kubernetesClient)
  }
}

/**
 * [MessagingApplicationClientImpl] is a [MessagingApplicationClient] that uses [KubernetesClient]
 * to initialize a custom resource client for [MessagingApplication].
 *
 * @property kubernetesClient the client to use to interact with Kubernetes API
 */
private class MessagingApplicationClientImpl(private val kubernetesClient: KubernetesClient) :
    MessagingApplicationClient, Closeable by kubernetesClient {

  /** The client for the [MessagingApplication] custom resource. */
  private val client =
      kubernetesClient.resources(
          MessagingApplication::class.java, MessagingApplication.List::class.java)

  override fun createOrReplace(name: String, image: String, config: String?) {
    val messagingApplication =
        MessagingApplication().apply {
          metadata = ObjectMetaBuilder().withName(name).build()
          spec = MessagingApplication.Spec(image = image, config = config)
        }
    client.createOrReplace(messagingApplication)
  }

  override fun delete(name: String): Boolean {
    return client.withName(name).delete()
  }

  override fun get(name: String): MessagingApplication? {
    return client.withName(name).get()
  }

  override fun list(): MessagingApplication.List {
    return client.list()
  }
}
