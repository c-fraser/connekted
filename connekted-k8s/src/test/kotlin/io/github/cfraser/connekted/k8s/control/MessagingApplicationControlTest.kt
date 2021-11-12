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
package io.github.cfraser.connekted.k8s.control

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.ResourceNotFoundException
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import io.github.cfraser.connekted.k8s.MessagingApplication
import kotlin.test.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@EnableKubernetesMockClient(crud = true)
internal class MessagingApplicationControlTest {

  /**
   * The [io.fabric8.kubernetes.client.server.mock.KubernetesMockServerExtension] activated through
   * [EnableKubernetesMockClient] is responsible for injecting the mock client into this property.
   */
  private lateinit var kubernetesClient: KubernetesClient

  @Test
  fun `Verify messaging application resource created and deleted correctly`() {
    val messagingApplicationControl = MessagingApplicationControl.new(kubernetesClient)
    val name = "test-connekted"
    val msgapp =
        MessagingApplication().apply {
          metadata.name = name
          spec = MessagingApplication.Spec("test-image")
        }

    messagingApplicationControl.createOrReplace(msgapp)

    assertDoesNotThrow {
      kubernetesClient.serviceAccounts().withName(name).require()
      kubernetesClient.services().withName(name).require()
      kubernetesClient.rbac().roleBindings().withName(name).require()
      kubernetesClient.apps().deployments().withName(name).require()
    }

    messagingApplicationControl.delete(msgapp)

    assertThrows<ResourceNotFoundException> {
      kubernetesClient.serviceAccounts().withName(name).require()
    }
    assertThrows<ResourceNotFoundException> { kubernetesClient.services().withName(name).require() }
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.rbac().roleBindings().withName(name).require()
    }
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.apps().deployments().withName(name).require()
    }
  }
}
