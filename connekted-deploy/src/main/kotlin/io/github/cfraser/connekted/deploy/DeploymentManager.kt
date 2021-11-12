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
import io.github.cfraser.connekted.deploy.infrastructure.InfrastructureProvisioner
import io.github.cfraser.connekted.deploy.infrastructure.NoOpInfrastructureProvisioner
import io.github.cfraser.connekted.deploy.resource.ContainerRegistryConfig
import io.github.cfraser.connekted.deploy.resource.ManagedResource
import io.github.cfraser.connekted.deploy.resource.operator.OperatorResources
import java.io.Closeable

/**
 * The [DeploymentManager] interface specifies functions for managing the infrastructure and
 * resources comprising a `connekted` deployment.
 */
interface DeploymentManager : Closeable {

  /** Deploy the infrastructure and resources. */
  fun deploy()

  /** Teardown the infrastructure and resources. */
  fun teardown()

  companion object {

    /**
     * Factory function to initialize a [DeploymentManager].
     *
     * @param kubernetesClient the [KubernetesClient] to use to create and delete resources
     * @param containerRegistryConfig the config for the messaging application container registry
     * @return the [DeploymentManager]
     */
    fun new(
        kubernetesClient: KubernetesClient,
        containerRegistryConfig: ContainerRegistryConfig,
    ): DeploymentManager =
        DeploymentManagerImpl(
            kubernetesClient, containerRegistryConfig, NoOpInfrastructureProvisioner())
  }
}

/**
 * The [DeploymentManager] that uses [infrastructureProvisioner] to provision infrastructure and
 * [kubernetesClient] to manage the resources corresponding to the [ManagedResource]
 * implementations.
 *
 * @property kubernetesClient the [KubernetesClient] to use to create and delete resources
 * @property containerRegistryConfig the config for the messaging application container registry
 * @property infrastructureProvisioner the [InfrastructureProvisioner] used to provision and destroy
 * the infrastructure
 */
private class DeploymentManagerImpl(
    private val kubernetesClient: KubernetesClient,
    private val containerRegistryConfig: ContainerRegistryConfig,
    private val infrastructureProvisioner: InfrastructureProvisioner,
) : DeploymentManager {

  /**
   * Provision the infrastructure then create the [containerRegistryConfig], each [ManagedResource],
   * and the [OperatorResources].
   */
  override fun deploy() {
    infrastructureProvisioner.provision()
    containerRegistryConfig.create(kubernetesClient)
    OperatorResources.create(kubernetesClient)
  }

  /**
   * Delete the [OperatorResources], each [ManagedResource], and the [containerRegistryConfig] then
   * [InfrastructureProvisioner.destroy] the infrastructure.
   */
  override fun teardown() {
    OperatorResources.delete(kubernetesClient)
    containerRegistryConfig.delete(kubernetesClient)
    infrastructureProvisioner.destroy()
  }

  /** Close the [KubernetesClient]. */
  override fun close() {
    kubernetesClient.close()
  }
}
