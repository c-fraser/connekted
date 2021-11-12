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

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import io.github.cfraser.connekted.k8s.MessagingApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@EnableKubernetesMockClient(crud = true)
class MessagingApplicationClientTest {

  /**
   * The [io.fabric8.kubernetes.client.server.mock.KubernetesMockServerExtension] activated through
   * [EnableKubernetesMockClient] is responsible for injecting the mock client into this property.
   */
  private lateinit var kubernetesClient: KubernetesClient

  @Test
  fun `Verify messaging application client operations`() {
    kubernetesClient
        .apiextensions()
        .v1()
        .customResourceDefinitions()
        .createOrReplace(MessagingApplication.customResourceDefinition)

    val messagingApplicationClient = MessagingApplicationClient.new(kubernetesClient)

    val name = "test-name"
    val image = "test-image"
    val config =
        """
          test {
            config {
              a = 1
              b = 2
              c = 3
            }
          }
          """.trimIndent()

    messagingApplicationClient.createOrReplace(name, image, config)
    val connekted = assertNotNull(messagingApplicationClient.get(name))
    assertEquals(image, connekted.spec.image)
    assertEquals(config, connekted.spec.config)
    assertNotNull(messagingApplicationClient.list().items.find { it.metadata.name == name })
    messagingApplicationClient.delete(name)
    assertNull(messagingApplicationClient.get(name))
  }
}
