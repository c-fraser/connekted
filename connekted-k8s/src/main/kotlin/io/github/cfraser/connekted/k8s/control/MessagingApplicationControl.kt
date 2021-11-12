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

import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.LocalObjectReference
import io.fabric8.kubernetes.api.model.Probe
import io.fabric8.kubernetes.api.model.ProbeBuilder
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
import io.fabric8.kubernetes.api.model.rbac.RoleBinding
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.Deletable
import io.fabric8.kubernetes.client.dsl.Resource
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.k8s.MessagingApplication
import mu.KotlinLogging

/**
 * The [MessagingApplicationControl] interface defines the resource management operations for a
 * [MessagingApplication].
 */
interface MessagingApplicationControl {

  /**
   * Create or replace resource(s) for the [messagingApplication].
   *
   * @param messagingApplication the [MessagingApplication] to create/replace resource(s) for
   */
  fun createOrReplace(messagingApplication: MessagingApplication)

  /**
   * Delete the resource(s) associated with the [messagingApplication].
   *
   * @param messagingApplication the [MessagingApplication] to delete resource(s) for
   */
  fun delete(messagingApplication: MessagingApplication)

  companion object {

    /**
     * Factory function to initialize a [MessagingApplicationControl].
     *
     * @param kubernetesClient the [KubernetesClient] to use to interact with the Kubernetes API
     * @return the [MessagingApplicationControl]
     */
    fun new(
        kubernetesClient: KubernetesClient,
    ): MessagingApplicationControl = MessagingApplicationControlImpl(kubernetesClient)
  }
}

private val logger = KotlinLogging.logger {}

/**
 * [MessagingApplicationControlImpl] is a [MessagingApplicationControl] implementation that uses
 * [KubernetesClient] to interact with the Kubernetes environment.
 *
 * @property kubernetesClient the client to use to interact with Kubernetes
 */
private class MessagingApplicationControlImpl(
    private val kubernetesClient: KubernetesClient,
) : MessagingApplicationControl {

  /**
   * Create (or replace) a [ServiceAccount], [Service], [RoleBinding], and [Deployment] for the
   * [messagingApplication].
   *
   * @param messagingApplication the [MessagingApplication] to create or update resources for
   */
  override fun createOrReplace(messagingApplication: MessagingApplication) {

    /**
     * Execute the [createOrReplace] function with generic logging.
     *
     * @param T the type to create or replace
     * @param createOrReplace the create or replace function
     */
    fun <T : HasMetadata> T.doCreateOrReplace(createOrReplace: (T) -> Unit) {
      logger.debug { "creating or replacing ${metadata.name} ${kind.lowercase()}" }
      createOrReplace(this)
      logger.debug { "created or replaced ${metadata.name} ${kind.lowercase()}" }
    }

    messagingApplication.serviceAccount().doCreateOrReplace { serviceAccount ->
      kubernetesClient.serviceAccounts().createOrReplace(serviceAccount)
    }
    messagingApplication.service().doCreateOrReplace { service ->
      kubernetesClient.services().createOrReplace(service)
    }
    messagingApplication.roleBinding().doCreateOrReplace { roleBinding ->
      kubernetesClient.rbac().roleBindings().createOrReplace(roleBinding)
    }
    messagingApplication.configMap().doCreateOrReplace { configMap ->
      kubernetesClient.configMaps().createOrReplace(configMap)
    }
    messagingApplication.deployment().apply {
      doCreateOrReplace { deployment ->
        kubernetesClient.apps().deployments().createOrReplace(deployment)
      }
    }
  }

  /**
   * Delete the [ServiceAccount], [Service], [RoleBinding], and [Deployment] for the
   * [messagingApplication].
   *
   * @param messagingApplication the [MessagingApplication] to delete resources for
   */
  override fun delete(messagingApplication: MessagingApplication) {

    /**
     * Delete the [Deletable] receiver with generic logging.
     *
     * @throws Exception if the delete operation fails
     */
    fun <T, R : HasMetadata> T.doDelete() where T : Deletable, T : Resource<R> {
      val resource = get().let { "${it.metadata.name} ${it.kind}" }
      logger.info { "deleting $resource" }
      if (delete()) logger.info { "completed delete of $resource" }
      else logger.error { "failed to delete $resource" }
    }

    kubernetesClient.serviceAccounts().withName(messagingApplication.metadata.name).doDelete()
    kubernetesClient.services().withName(messagingApplication.metadata.name).doDelete()
    kubernetesClient.rbac().roleBindings().withName(messagingApplication.metadata.name).doDelete()
    kubernetesClient.configMaps().withName(messagingApplication.metadata.name).doDelete()
    kubernetesClient.apps().deployments().withName(messagingApplication.metadata.name).doDelete()
  }

  companion object {

    /**
     * Extension property for [MessagingApplication] that returns the map of labels to include on
     * resources.
     */
    private val MessagingApplication.labels: Map<String, String>
      get() = mapOf(MessagingApplication.nameKey to metadata.name)

    /**
     * Return a [ServiceAccount] for the receiver [MessagingApplication].
     *
     * @return the service account for the messaging application
     */
    private fun MessagingApplication.serviceAccount(): ServiceAccount =
        ServiceAccountBuilder()
            .withNewMetadata()
            .withName(metadata.name)
            .addToLabels(labels)
            .endMetadata()
            .build()

    /**
     * Return a [Service] for the receiver [MessagingApplication].
     *
     * @return the service for the messaging application
     */
    private fun MessagingApplication.service(): Service =
        ServiceBuilder()
            .withNewMetadata()
            .withName(metadata.name)
            .addToLabels(labels)
            .endMetadata()
            .withNewSpec()
            .addNewPort()
            .withName("http")
            .withProtocol("TCP")
            .withPort(Configs.appPort)
            .withTargetPort(IntOrString(Configs.appPort))
            .endPort()
            .withSelector<String, String>(labels)
            .withType("ClusterIP")
            .endSpec()
            .build()

    /**
     * Return a [RoleBinding] for the receiver [MessagingApplication].
     *
     * @return the role binding for the messaging application
     */
    private fun MessagingApplication.roleBinding(): RoleBinding =
        RoleBindingBuilder()
            .withNewMetadata()
            .withName(metadata.name)
            .endMetadata()
            .withNewRoleRef()
            .withKind("ClusterRole")
            .withApiGroup("rbac.authorization.k8s.io")
            .withName("view")
            .endRoleRef()
            .addNewSubject()
            .withKind("ServiceAccount")
            .withName(metadata.name)
            .endSubject()
            .build()

    /**
     * Return a [ConfigMap] for the receiver [MessagingApplication].
     *
     * @return the configmap for the messaging application
     */
    private fun MessagingApplication.configMap(): ConfigMap =
        ConfigMapBuilder()
            .withNewMetadata()
            .withName(metadata.name)
            .addToLabels(labels)
            .endMetadata()
            .withData<String, String>(
                @OptIn(ExperimentalStdlibApi::class)
                buildMap<String, String> { spec.config?.run { put(Configs.configFile, this) } })
            .build()

    /**
     * Return a [Deployment] for the receiver [MessagingApplication].
     *
     * @return the deployment for the messaging application
     */
    private fun MessagingApplication.deployment(): Deployment {

      /**
       * Return a HTTP [Probe] from the [path].
       *
       * @param path the HTTP path query
       * @return the constructed probe
       */
      fun httpProbeOf(path: String): Probe =
          ProbeBuilder()
              .withFailureThreshold(3)
              .withNewHttpGet()
              .withPath(path)
              .withPort(IntOrString(Configs.appPort))
              .withScheme("HTTP")
              .endHttpGet()
              .withInitialDelaySeconds(1)
              .withPeriodSeconds(30)
              .withSuccessThreshold(1)
              .withTimeoutSeconds(10)
              .build()

      return DeploymentBuilder()
          .withNewMetadata()
          .withName(metadata.name)
          .addToLabels(labels)
          .endMetadata()
          .withNewSpec()
          .withReplicas(1)
          .withNewSelector()
          .addToMatchLabels(labels)
          .endSelector()
          .withNewTemplate()
          .withNewMetadata()
          .addToLabels(labels)
          .endMetadata()
          .withNewSpec()
          .addNewVolume()
          .withName("config-volume")
          .withNewConfigMap()
          .withName(metadata.name)
          .endConfigMap()
          .endVolume()
          .addNewContainer()
          .addNewEnv()
          .withName(Configs.nameEnv)
          .withValue(metadata.name)
          .endEnv()
          .addNewEnv()
          .withName(Configs.usernameEnv)
          .withNewValueFrom()
          .withNewSecretKeyRef()
          .withName(Configs.authName)
          .withKey(Configs.usernameKey)
          .endSecretKeyRef()
          .endValueFrom()
          .endEnv()
          .addNewEnv()
          .withName(Configs.passwordEnv)
          .withNewValueFrom()
          .withNewSecretKeyRef()
          .withName(Configs.authName)
          .withKey(Configs.passwordKey)
          .endSecretKeyRef()
          .endValueFrom()
          .endEnv()
          .addNewEnv()
          .withName(Configs.messagingTokenEnv)
          .withNewValueFrom()
          .withNewSecretKeyRef()
          .withName(Configs.messagingTokenSecretName)
          .withKey(Configs.messagingTokenKey)
          .endSecretKeyRef()
          .endValueFrom()
          .endEnv()
          .withImage(spec.image)
          .withImagePullPolicy("Always")
          .withName(metadata.name)
          .withLivenessProbe(httpProbeOf(Configs.livenessPath))
          .addNewPort()
          .withContainerPort(Configs.appPort)
          .withName("http")
          .withProtocol("TCP")
          .endPort()
          .withReadinessProbe(httpProbeOf(Configs.readinessPath))
          .addNewVolumeMount()
          .withName("config-volume")
          .withMountPath(Configs.configMountPath)
          .endVolumeMount()
          .endContainer()
          .withServiceAccountName(metadata.name)
          .withImagePullSecrets(LocalObjectReference(Configs.dockerCredentialsName))
          .endSpec()
          .endTemplate()
          .endSpec()
          .build()
    }
  }
}
