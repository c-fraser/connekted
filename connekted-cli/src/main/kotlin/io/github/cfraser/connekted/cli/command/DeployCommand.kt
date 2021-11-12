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
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.cli.config.ContainerizerFactory
import io.github.cfraser.connekted.cli.config.DockerConfig
import io.github.cfraser.connekted.cli.config.DockerConfig.Companion.toDockerRegistryConfig
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.deploy.DeploymentManager
import io.github.cfraser.connekted.docker.ContainerDestination
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * [DeployCommand] is a [CliktCommand] to [DeploymentManager.deploy] the `connekted` resources on a
 * Kubernetes cluster.
 *
 * @property containerizerFactory the [ContainerizerFactory] to use to initialize a
 * [io.github.cfraser.connekted.docker.Containerizer]
 * @property kubernetesClient the [KubernetesClient] to use to deploy the *messaging application
 * operator* and managed resources
 */
@Singleton
internal class DeployCommand(
    private val containerizerFactory: ContainerizerFactory,
    private val kubernetesClient: KubernetesClient
) :
    CliktCommand(
        name = "deploy", help = "Deploy the `connekted` resources on a Kubernetes cluster") {

  private val imagesZip: File by
      argument(help = "the `images.zip` file from the `connekted` release").file(mustExist = true)

  private val dockerServer: String by
      option("-s", "--docker-server", help = "the server for the container image repository")
          .default(ContainerDestination.Type.DOCKER_HUB.serverUrl)

  private val dockerRepository: String by
      option("-r", "--docker-repository", help = "the repository to store container images in")
          .required()

  private val dockerUsername: String by
      option("-u", "--docker-username", help = "the username to access the repository with")
          .required()

  private val dockerPassword: String by
      option("-p", "--docker-password", help = "the password for the specified username").required()

  private val waitForServer: Boolean by
      option(
              "-w",
              "--wait",
              help = "whether to wait for the messaging application operator to initialize")
          .flag("--continue", default = true)

  override fun run() {
    val dockerConfig =
        DockerConfig(
            dockerServer = dockerServer,
            dockerRepository = dockerRepository,
            dockerUsername = dockerUsername,
            dockerPassword = dockerPassword)

    val imageReferences =
        runBlocking(Dispatchers.IO) {
          containerizerFactory.new(dockerConfig).containerizeImageZip(imagesZip.toPath())
        }

    DeploymentManager.new(kubernetesClient, dockerConfig.toDockerRegistryConfig(imageReferences))
        .deploy()

    if (waitForServer)
        kubernetesClient
            .apps()
            .deployments()
            .withName(Configs.operatorName)
            .waitUntilReady(3, TimeUnit.MINUTES)
  }
}
