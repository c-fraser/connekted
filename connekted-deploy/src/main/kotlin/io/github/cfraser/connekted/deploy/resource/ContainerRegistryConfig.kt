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
package io.github.cfraser.connekted.deploy.resource

import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.common.Configs
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The [ManagedResource] implementation for the container registry resources. This includes a
 * configmap storing the references to `connekted` images and `kubernetes.io/dockerconfigjson`
 * secret containing the credentials to access messaging application images.
 *
 * @property imageReferences the references to the images used by `connekted`
 * @property repository the name of the docker repository
 * @property serverUrl the docker server url
 * @property username the username to access the registry with
 * @property password the password for the username
 */
class ContainerRegistryConfig(
    private val imageReferences: Set<String>,
    private val repository: String,
    private val serverUrl: String,
    private val username: String,
    private val password: String
) : ManagedResource {

  /** Create the configmap and `dockerconfigjson` secret. */
  override fun create(kubernetesClient: KubernetesClient) {
    kubernetesClient
        .configMaps()
        .createOrReplace(
            ConfigMapBuilder()
                .withNewMetadata()
                .withName(Configs.dockerImagesConfig)
                .endMetadata()
                .withData<String, String>(
                    @OptIn(ExperimentalStdlibApi::class)
                    buildMap {
                      put(repositoryKey, repository)
                      for (imageReference in imageReferences) {
                        val key =
                            when {
                              "connekted-operator" in imageReference -> operatorImageKey
                              else -> error("unexpected image reference $imageReference")
                            }
                        put(key, imageReference)
                      }
                    })
                .build())

    kubernetesClient
        .secrets()
        .createOrReplace(
            SecretBuilder()
                .withNewMetadata()
                .withName(Configs.dockerCredentialsName)
                .endMetadata()
                .withType("kubernetes.io/dockerconfigjson")
                .withStringData<String, String>(
                    mapOf(
                        dockerconfigjsonKey to
                            Json.encodeToString(
                                DockerConfigJson.serializer(),
                                DockerConfigJson(serverUrl, username, password))))
                .build())
  }

  /** Delete the configmap and `dockerconfigjson` secret. */
  override fun delete(kubernetesClient: KubernetesClient) {
    kubernetesClient.secrets().withName(Configs.dockerCredentialsName).delete()
    kubernetesClient.configMaps().withName(Configs.dockerImagesConfig).delete()
  }

  companion object {

    /** The key to access the repository name from the configmap. */
    const val repositoryKey = "connekted-docker-repository"

    /**
     * The key to access the *messaging application operator* image reference from the configmap.
     */
    const val operatorImageKey = "connekted-operator-image"

    /**
     * Retrieve the configuration value for the [key] from the [Configs.dockerImagesConfig] map.
     *
     * @param key the key to retrieve the value for
     * @return the configuration value or `null` if the key was not present
     */
    fun KubernetesClient.retrieveDockerImagesConfig(key: String): String? {
      return configMaps().withName(Configs.dockerImagesConfig).get()?.data?.get(key)
    }

    /**
     * Retrieve the image reference corresponding to the [key] from the [Configs.dockerImagesConfig]
     * map.
     *
     * @param key the key to retrieve the image reference for
     * @return the image reference
     * @throws IllegalStateException if no image reference was found for [key]
     */
    fun KubernetesClient.retrieveImageReference(key: String): String {
      return checkNotNull(retrieveDockerImagesConfig(key)) {
        "Failed to find value for $key in ${Configs.dockerImagesConfig} configmap"
      }
    }

    /**
     * The key to access the `.dockerconfigjson` from the `kubernetes.io/dockerconfigjson` secret.
     */
    private const val dockerconfigjsonKey = ".dockerconfigjson"

    /**
     * Decode the value corresponding to the [dockerconfigjsonKey] stored in the
     * `kubernetes.io/dockerconfigjson` secret.
     *
     * @return the [Triple] containing the [serverUrl], [username], and [password]
     */
    @Throws(IllegalStateException::class)
    fun Secret.decodeDockerconfigjson(): Triple<String, String, String> {
      val dockerconfigjsonValue =
          String(Base64.getDecoder().decode(checkNotNull(data[dockerconfigjsonKey])))
      val dockerConfigJson =
          checkNotNull(
              Json.decodeFromString(DockerConfigJson.serializer(), dockerconfigjsonValue).takeIf {
                it.auths.size == 1
              })
      return dockerConfigJson.auths.entries.first().let { (serverUrl, credentials) ->
        Triple(serverUrl, credentials.username, credentials.password)
      }
    }
  }

  /**
   * [DockerConfigJson] is a [Serializable] data class representing the value of a
   * `kubernetes.io/dockerconfigjson` secret. Refer to the docker config secrets
   * [documentation](https://kubernetes.io/docs/concepts/configuration/secret/#docker-config-secrets)
   * for more information about the required data structure.
   */
  @Serializable
  private data class DockerConfigJson(val auths: Map<String, Credentials>) {

    constructor(
        serverUrl: String,
        username: String,
        password: String
    ) : this(mapOf(serverUrl to Credentials(username, password)))

    @Serializable data class Credentials(val username: String, val password: String)
  }
}
