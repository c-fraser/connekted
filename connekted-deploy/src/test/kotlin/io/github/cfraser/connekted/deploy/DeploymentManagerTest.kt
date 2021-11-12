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
package io.github.cfraser.connekted.deploy

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.ResourceNotFoundException
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.deploy.resource.ContainerRegistryConfig
import io.github.cfraser.connekted.k8s.MessagingApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@EnableKubernetesMockClient(crud = true)
class DeploymentManagerTest {

  /**
   * The [io.fabric8.kubernetes.client.server.mock.KubernetesMockServerExtension] activated through
   * [EnableKubernetesMockClient] is responsible for injecting the mock client into this property.
   */
  private lateinit var kubernetesClient: KubernetesClient

  @Test
  fun `Verify deploy and teardown`() {
    val deploymentManager =
        DeploymentManager.new(
            kubernetesClient,
            ContainerRegistryConfig(
                setOf("repo/connekted:connekted-operator-0.0.0-12345"),
                "repo/connekted",
                "https://index.docker.io/v1/",
                "test-user",
                "test-password"))

    deploymentManager.deploy()
    verifyResourcesDeployed()
    deploymentManager.teardown()
    verifyResourcesDeleted()
  }

  private fun verifyResourcesDeployed() {
    assertDoesNotThrow {
      // Verify DockerRegistrySecret was created
      kubernetesClient.secrets().withName(Configs.dockerCredentialsName).require()

      // Verify MessagingApplication CustomResourceDefinition was created
      kubernetesClient
          .apiextensions()
          .v1()
          .customResourceDefinitions()
          .withName(MessagingApplication.customResourceDefinition.metadata.name)
          .require()
    }

    // Verify BasicAuth secret was created
    val basicAuth = assertNotNull(kubernetesClient.secrets().withName(Configs.authName).get())
    assertEquals(basicAuth.stringData[Configs.usernameKey], Configs.adminUsername)

    // Verify messaging token opaque secret was created
    val messagingTokenSecret =
        assertNotNull(kubernetesClient.secrets().withName(Configs.messagingTokenSecretName).get())
    assertNotNull(messagingTokenSecret.stringData[Configs.messagingTokenKey])

    // Verify ConfigMap was created (with initial data)
    val configMap =
        assertNotNull(kubernetesClient.configMaps().withName(Configs.dockerImagesConfig).get())
    assertEquals("repo/connekted", configMap.data[ContainerRegistryConfig.repositoryKey])

    assertDoesNotThrow {
      // Verify ServiceAccount was created
      kubernetesClient.serviceAccounts().withName(Configs.operatorName).require()

      // Verify Service was created
      kubernetesClient.services().withName(Configs.operatorName).require()

      // Verify RoleBinding was created
      kubernetesClient.rbac().roleBindings().withName(Configs.operatorName).require()

      // Verify ClusterRoleBinding was created
      kubernetesClient.rbac().clusterRoleBindings().withName(Configs.operatorName).require()

      // Verify Deployment was created
      kubernetesClient.apps().deployments().withName(Configs.operatorName).require()
    }
  }

  private fun verifyResourcesDeleted() {
    // Verify Deployment was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.apps().deployments().withName(Configs.operatorName).require()
    }

    // Verify ClusterRoleBinding was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.rbac().clusterRoleBindings().withName(Configs.operatorName).require()
    }

    // Verify RoleBinding was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.rbac().roleBindings().withName(Configs.operatorName).require()
    }

    // Verify Service was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.services().withName(Configs.operatorName).require()
    }

    // Verify ServiceAccount was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.serviceAccounts().withName(Configs.operatorName).require()
    }

    // Verify ConfigMap was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.configMaps().withName(Configs.operatorName).require()
    }

    // Verify BasicAuth secret was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.secrets().withName(Configs.authName).require()
    }

    // Verify messaging token opaque secret was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.secrets().withName(Configs.messagingTokenSecretName).require()
    }

    // Verify MessagingApplication CustomResourceDefinition was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient
          .apiextensions()
          .v1()
          .customResourceDefinitions()
          .withName(MessagingApplication.customResourceDefinition.metadata.name)
          .require()
    }

    // Verify DockerRegistrySecret was deleted
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.secrets().withName(Configs.dockerCredentialsName).require()
    }
  }
}
