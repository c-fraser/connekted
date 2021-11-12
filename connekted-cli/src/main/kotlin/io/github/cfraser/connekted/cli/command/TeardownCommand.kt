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
package io.github.cfraser.connekted.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.cli.config.DockerConfig
import io.github.cfraser.connekted.cli.config.DockerConfig.Companion.toDockerRegistryConfig
import io.github.cfraser.connekted.deploy.DeploymentManager
import io.github.cfraser.connekted.k8s.client.MessagingApplicationClient
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * [DeployCommand] is a [CliktCommand] to [DeploymentManager.teardown] the resources deployed on a
 * Kubernetes cluster.
 *
 * Any running messaging applications are stopped and deleted prior to *messaging application
 * operator* teardown.
 *
 * @property kubernetesClient the [KubernetesClient] to use to teardown the deployment
 */
@Singleton
internal class TeardownCommand(private val kubernetesClient: KubernetesClient) :
    CliktCommand(name = "teardown", help = "Teardown the `connekted` deployment") {

  override fun run() {
    val dockerConfig =
        runBlocking(Dispatchers.IO) {
          requireNotNull(DockerConfig.retrieve(kubernetesClient)) {
            "The docker configuration was unable to be extracted from the environment"
          }
        }

    with(MessagingApplicationClient.new(kubernetesClient)) {
      for (messagingApplication in list().items) {
        delete(messagingApplication.metadata.name)
      }
    }

    DeploymentManager.new(kubernetesClient, dockerConfig.toDockerRegistryConfig()).teardown()
  }
}
