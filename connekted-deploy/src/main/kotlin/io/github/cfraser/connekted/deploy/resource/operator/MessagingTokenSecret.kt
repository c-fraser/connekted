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
package io.github.cfraser.connekted.deploy.resource.operator

import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.ResourceNotFoundException
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.deploy.resource.ManagedResource
import java.util.UUID

/**
 * The [ManagedResource] implementation for the
 * [opaque secret](https://kubernetes.io/docs/concepts/configuration/secret/#opaque-secrets) used by
 * the messaging components.
 */
internal object MessagingTokenSecret : ManagedResource {

  /**
   * Create the opaque secret with [Configs.messagingTokenSecretName] if it does not already exist.
   */
  override fun create(kubernetesClient: KubernetesClient) {
    try {
      kubernetesClient.secrets().withName(Configs.messagingTokenSecretName).require()
    } catch (_: ResourceNotFoundException) {
      kubernetesClient
          .secrets()
          .create(
              SecretBuilder()
                  .withNewMetadata()
                  .withName(Configs.messagingTokenSecretName)
                  .endMetadata()
                  .withType("Opaque")
                  .withStringData<String, String>(
                      mapOf(Configs.messagingTokenKey to UUID.randomUUID().toString().take(15)))
                  .build())
    }
  }

  /** Delete the opaque secret with [Configs.messagingTokenSecretName]. */
  override fun delete(kubernetesClient: KubernetesClient) {
    kubernetesClient.secrets().withName(Configs.messagingTokenSecretName).delete()
  }
}
