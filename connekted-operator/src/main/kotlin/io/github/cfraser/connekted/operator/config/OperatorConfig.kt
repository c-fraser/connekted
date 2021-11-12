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
package io.github.cfraser.connekted.operator.config

import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.k8s.MessagingApplication
import io.github.cfraser.connekted.k8s.control.MessagingApplicationControl
import io.github.cfraser.connekted.operator.k8s.MessagingApplicationResourceManager
import io.javaoperatorsdk.operator.Operator
import io.javaoperatorsdk.operator.api.ResourceController
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * The [OperatorConfig] class configures the *messaging application operator* for the application.
 */
@Configuration
@ComponentScan("io.github.cfraser.connekted.operator.k8s")
internal class OperatorConfig {

  /**
   * Initialize the [KubernetesClient] to use to interact with the Kubernetes API.
   *
   * @return the [KubernetesClient]
   */
  @Bean
  fun kubernetesClient(): KubernetesClient {
    return DefaultKubernetesClient()
  }

  /**
   * Initialize a [MessagingApplicationControl] using the [kubernetesClient].
   *
   * @param kubernetesClient the [KubernetesClient] to use to interact with the Kubernetes API
   * @return the [MessagingApplicationControl]
   */
  @Bean
  fun messagingApplicationControl(kubernetesClient: KubernetesClient): MessagingApplicationControl {
    return MessagingApplicationControl.new(kubernetesClient)
  }

  /**
   * Initialize the [Operator] for the [io.github.cfraser.connekted.k8s.MessagingApplication] custom
   * resource.
   *
   * @param kubernetesClient the [KubernetesClient] to use to interact with the Kubernetes API
   * @param messagingApplicationResourceController the [MessagingApplicationResourceManager] that
   * handles creation and deletion of resources corresponding to a
   * [io.github.cfraser.connekted.k8s.MessagingApplication]
   * @return the [Operator]
   */
  @Bean
  fun operator(
      kubernetesClient: KubernetesClient,
      messagingApplicationResourceController: ResourceController<MessagingApplication>
  ): Operator {
    return Operator(kubernetesClient, DefaultConfigurationService.instance()).apply {
      register(messagingApplicationResourceController)
      start()
    }
  }
}
