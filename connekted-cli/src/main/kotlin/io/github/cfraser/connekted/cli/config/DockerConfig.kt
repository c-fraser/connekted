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
package io.github.cfraser.connekted.cli.config

import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.cli.config.DockerConfig.Companion.toContainerDestination
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.deploy.resource.ContainerRegistryConfig
import io.github.cfraser.connekted.deploy.resource.ContainerRegistryConfig.Companion.decodeDockerconfigjson
import io.github.cfraser.connekted.deploy.resource.ContainerRegistryConfig.Companion.retrieveDockerImagesConfig
import io.github.cfraser.connekted.docker.ContainerDestination
import io.github.cfraser.connekted.docker.Containerizer
import java.time.Duration
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Produces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import mu.KotlinLogging

/** The [ContainerizerConfig] class configures the [ContainerizerFactory] for the application. */
@ApplicationScoped
internal class ContainerizerConfig {

  @Produces
  fun containerizerFactory(): ContainerizerFactory {
    return ContainerizerFactory { dockerConfig ->
      Containerizer.new(dockerConfig.toContainerDestination())
    }
  }
}

/**
 * [ContainerizerFactory] is a functional interface for initializing a [Containerizer] from a
 * [DockerConfig].
 */
internal fun interface ContainerizerFactory {

  /**
   * Factory function for initializing a [Containerizer] for the [dockerConfig].
   *
   * @param dockerConfig the [DockerConfig] defining the remote docker registry
   * @return the [Containerizer]
   */
  fun new(dockerConfig: DockerConfig): Containerizer
}

/**
 * The [DockerConfig] data class contains the docker configuration provided by the user or retrieved
 * from the Kubernetes environment.
 *
 * @property dockerServer the url to the docker server containing the [dockerRepository]
 * @property dockerRepository the repository for storing the messaging application images
 * @property dockerUsername the username to access the server/repository
 * @property dockerPassword the password for the username
 */
@Serializable
internal data class DockerConfig(
    val dockerServer: String,
    val dockerRepository: String,
    val dockerUsername: String,
    val dockerPassword: String
) {

  init {
    require(dockerServer.isNotBlank())
    require(dockerRepository.isNotBlank())
    require(dockerUsername.isNotBlank())
    require(dockerPassword.isNotBlank())
  }

  companion object {

    private val logger = KotlinLogging.logger {}

    /**
     * Construct a [ContainerRegistryConfig] from the [DockerConfig] data.
     *
     * @return the [ContainerRegistryConfig]
     */
    fun DockerConfig.toDockerRegistryConfig(
        imageReferences: Set<String> = emptySet()
    ): ContainerRegistryConfig {
      return ContainerRegistryConfig(
          imageReferences, dockerRepository, dockerServer, dockerUsername, dockerPassword)
    }

    /**
     * Construct a [ContainerDestination] from the [DockerConfig] data.
     *
     * The [dockerServer] must match the [ContainerDestination.Type.DOCKER_HUB.serverUrl] until
     * other container registries are supported.
     *
     * @return the [ContainerDestination]
     */
    fun DockerConfig.toContainerDestination(): ContainerDestination {
      require(dockerServer == ContainerDestination.Type.DOCKER_HUB.serverUrl) {
        "Currently DockerHub is the only supported container registry"
      }

      return ContainerDestination(dockerRepository, dockerUsername, dockerPassword)
    }

    /**
     * Attempt to retrieve [DockerConfig] data from the Kubernetes environment.
     *
     * @param kubernetesClient the [KubernetesClient] to use to query the environment
     * @return the retrieved [DockerConfig] or `null` if config data was unable to be extracted
     */
    suspend fun retrieve(kubernetesClient: KubernetesClient): DockerConfig? {
      return coroutineScope {
        runCatching {
              withTimeout(Duration.ofSeconds(2).toMillis()) {
                val deferredRepository = retrieveRepositoryAsync(kubernetesClient)
                val deferredServerCredentials = retrieveServerCredentialsAsync(kubernetesClient)
                val repository = checkNotNull(deferredRepository.await())
                val (serverUrl, username, password) =
                    checkNotNull(deferredServerCredentials.await())
                DockerConfig(serverUrl, repository, username, password)
              }
            }
            .onFailure { logger.error(it) { "Failed to retrieve docker config" } }
            .getOrNull()
      }
    }

    /**
     * Asynchronously retrieve the messaging application image repository from the *messaging
     * application operator* configmap.
     *
     * @param kubernetesClient the [KubernetesClient] to use to query the
     * [ContainerRegistryConfig.repositoryKey] from the [Configs.dockerImagesConfig]
     */
    private fun CoroutineScope.retrieveRepositoryAsync(
        kubernetesClient: KubernetesClient
    ): Deferred<String?> {
      return async(Dispatchers.IO) {
        kubernetesClient.retrieveDockerImagesConfig(ContainerRegistryConfig.repositoryKey)
      }
    }

    /**
     * Asynchronously retrieve the docker server credentials from the
     * `kubernetes.io/dockerconfigjson` secret.
     *
     * @param kubernetesClient the [KubernetesClient] to use to query the
     * [Configs.dockerCredentialsName] secret
     */
    private fun CoroutineScope.retrieveServerCredentialsAsync(
        kubernetesClient: KubernetesClient
    ): Deferred<Triple<String, String, String>?> {
      return async(Dispatchers.IO) {
        kubernetesClient
            .secrets()
            .withName(Configs.dockerCredentialsName)
            .get()
            ?.runCatching { decodeDockerconfigjson() }
            ?.onFailure { logger.error(it) { "Failed to decode dockerconfigjson" } }
            ?.getOrNull()
      }
    }
  }
}
