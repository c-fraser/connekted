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
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.cli.config.ContainerizerFactory
import io.github.cfraser.connekted.cli.config.DockerConfig
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.k8s.client.MessagingApplicationClient
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * [RunCommand] is a [CliktCommand] to run a *messaging application*, packaged as a JAR, on a
 * Kubernetes cluster.
 *
 * @property containerizerFactory the [ContainerizerFactory] to use to initialize a
 * [io.github.cfraser.connekted.docker.Containerizer]
 * @property kubernetesClient the [KubernetesClient] to use to create the `connekted` resources
 */
@Singleton
internal class RunCommand(
    private val containerizerFactory: ContainerizerFactory,
    private val kubernetesClient: KubernetesClient
) :
    CliktCommand(
        name = "run", help = "Run the messaging application, packaged as a JAR, in Kubernetes") {

  private val jar: File by argument(help = "the messaging application jar").file()

  private val name: String? by
      option(
          "-n",
          "--name",
          help =
              "(optional) - the name of the messaging application, defaults to the jar file name")

  private val config: File? by
      option("-c", "--config", help = "(optional) - the messaging application config file").file()

  private val systemProperties: Map<String, String> by
      option(
              "-s",
              "--system-property",
              help = "(optional) - the system properties to launch the jar with")
          .associate()

  /** The co-occurring [OptionGroup] for accepting Java Keystore information. */
  private class KeystoreOption : OptionGroup(help = "(optional) - the keystore to") {

    val keystore: File by
        option("--keystore", help = "the messaging application Java Keystore file")
            .file()
            .required()

    val keystorePassword: String by
        option("--keystore-password", help = "the password for the messaging application keystore")
            .required()
  }

  private val keystoreOption: KeystoreOption? by KeystoreOption().cooccurring()

  override fun run() {
    val imageReference =
        runBlocking(Dispatchers.IO) {
          val dockerConfig =
              requireNotNull(DockerConfig.retrieve(kubernetesClient)) {
                "The docker configuration was unable to be extracted from the environment"
              }

          val options =
              @OptIn(ExperimentalStdlibApi::class)
              buildSet {
                for (systemProperty in systemProperties.map { "-D${it.key}=${it.value}" }) this +=
                    systemProperty
                keystoreOption?.keystorePassword?.also { password ->
                  this +=
                      "-Djavax.net.ssl.keyStore=${Configs.configMountPath}/${Configs.keystoreFile}"
                  this += "-Djavax.net.ssl.keyStorePassword=$password"
                }
              }
          containerizerFactory
              .new(dockerConfig)
              .containerizeJar(jar.toPath(), keystoreOption?.keystore?.toPath(), options)
        }

    MessagingApplicationClient.new(kubernetesClient)
        .createOrReplace(name ?: jar.name, imageReference, config?.readText())
  }
}
