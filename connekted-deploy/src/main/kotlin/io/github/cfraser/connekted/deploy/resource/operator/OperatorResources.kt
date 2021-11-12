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

import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.LocalObjectReference
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.ServiceAccount
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.api.model.ServiceBuilder
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder
import io.fabric8.kubernetes.api.model.rbac.RoleBinding
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.deploy.resource.ContainerRegistryConfig
import io.github.cfraser.connekted.deploy.resource.ContainerRegistryConfig.Companion.retrieveImageReference
import io.github.cfraser.connekted.deploy.resource.ManagedResource

/** The [ManagedResource] implementation for the *messaging application operator* resources. */
internal object OperatorResources : ManagedResource {

  /**
   * The [ManagedResource] implementations that are required by the operator, therefore these are
   * created before (and deleted after) operator resources.
   */
  private val requiredResources =
      listOf(CustomResourceDefinition, BasicAuthSecret, MessagingTokenSecret)

  /**
   * Create the [requiredResources] then operator [serviceAccount], [service], [roleBinding],
   * [clusterRoleBinding], and [deployment].
   *
   * @param kubernetesClient the [KubernetesClient] to create the resources with
   */
  override fun create(kubernetesClient: KubernetesClient) {
    // Verify the operator image reference exists before creating resources
    val operatorImage =
        kubernetesClient.retrieveImageReference(ContainerRegistryConfig.operatorImageKey)

    for (managedResource in requiredResources) managedResource.create(kubernetesClient)

    kubernetesClient.serviceAccounts().createOrReplace(serviceAccount)
    kubernetesClient.services().createOrReplace(service)
    kubernetesClient.rbac().roleBindings().createOrReplace(roleBinding)
    kubernetesClient
        .rbac()
        .clusterRoleBindings()
        .createOrReplace(clusterRoleBinding(kubernetesClient.namespace))
    kubernetesClient.apps().deployments().createOrReplace(deployment(operatorImage))
  }

  /**
   * Delete the operator [serviceAccount], [service], [roleBinding], [clusterRoleBinding], and
   * [deployment] then the [requiredResources].
   *
   * @param kubernetesClient the [KubernetesClient] to delete the resources with
   */
  override fun delete(kubernetesClient: KubernetesClient) {
    kubernetesClient.apps().deployments().withName(Configs.operatorName).delete()
    kubernetesClient.rbac().clusterRoleBindings().withName(Configs.operatorName).delete()
    kubernetesClient.rbac().roleBindings().withName(Configs.operatorName).delete()
    kubernetesClient.services().withName(Configs.operatorName).delete()
    kubernetesClient.serviceAccounts().withName(Configs.operatorName).delete()

    for (managedResource in requiredResources) managedResource.delete(kubernetesClient)
  }

  /** The [Map] of labels to include on operator resources. */
  private val labels: Map<String, String> =
      mapOf("app" to Configs.operatorName, "part-of" to "connekted")

  /** The [ServiceAccount] for the operator. */
  private val serviceAccount: ServiceAccount =
      ServiceAccountBuilder()
          .withNewMetadata()
          .withName(Configs.operatorName)
          .addToLabels(labels)
          .endMetadata()
          .build()

  /** The [Service] for the operator. */
  private val service: Service =
      ServiceBuilder()
          .withNewMetadata()
          .withName(Configs.operatorName)
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

  /** The [RoleBinding] for the operator. */
  private val roleBinding: RoleBinding =
      RoleBindingBuilder()
          .withNewMetadata()
          .withName(Configs.operatorName)
          .endMetadata()
          .withNewRoleRef()
          .withKind("ClusterRole")
          .withApiGroup("rbac.authorization.k8s.io")
          .withName("view")
          .endRoleRef()
          .addNewSubject()
          .withKind("ServiceAccount")
          .withName(Configs.operatorName)
          .endSubject()
          .build()

  /**
   * Return [ClusterRoleBinding] for the operator, which binds to the [serviceAccount] in
   * [namespace].
   */
  private fun clusterRoleBinding(namespace: String): ClusterRoleBinding =
      ClusterRoleBindingBuilder()
          .withNewMetadata()
          .withName(Configs.operatorName)
          .withLabels<String, String>(labels)
          .endMetadata()
          .withNewRoleRef()
          .withKind("ClusterRole")
          .withApiGroup("rbac.authorization.k8s.io")
          .withName("cluster-admin")
          .endRoleRef()
          .addNewSubject()
          .withKind("ServiceAccount")
          .withName(Configs.operatorName)
          .withNamespace(namespace)
          .endSubject()
          .build()

  /** The [Deployment] for the operator. */
  private fun deployment(operatorImage: String): Deployment =
      DeploymentBuilder()
          .withNewMetadata()
          .withName(Configs.operatorName)
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
          .addNewContainer()
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
          .withImage(operatorImage)
          .withImagePullPolicy("Always")
          .withName(Configs.operatorName)
          .withNewLivenessProbe()
          .withFailureThreshold(3)
          .withNewHttpGet()
          .withPath("/actuator/health")
          .withPort(IntOrString(Configs.appPort))
          .withScheme("HTTP")
          .endHttpGet()
          .withInitialDelaySeconds(1)
          .withPeriodSeconds(30)
          .withSuccessThreshold(1)
          .withTimeoutSeconds(10)
          .endLivenessProbe()
          .addNewPort()
          .withContainerPort(Configs.appPort)
          .withName("http")
          .withProtocol("TCP")
          .endPort()
          .withNewReadinessProbe()
          .withFailureThreshold(3)
          .withNewHttpGet()
          .withPath("/actuator/health")
          .withPort(IntOrString(Configs.appPort))
          .withScheme("HTTP")
          .endHttpGet()
          .withInitialDelaySeconds(1)
          .withPeriodSeconds(30)
          .withSuccessThreshold(1)
          .withTimeoutSeconds(10)
          .endReadinessProbe()
          .endContainer()
          .withServiceAccountName(Configs.operatorName)
          .withImagePullSecrets(LocalObjectReference(Configs.dockerCredentialsName))
          .endSpec()
          .endTemplate()
          .endSpec()
          .build()
}
