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
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.unique
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.k8s.client.MessagingApplicationClient
import javax.inject.Singleton

/**
 * [StopCommand] is a [CliktCommand] to stop messaging application(s) running on a Kubernetes
 * cluster.
 *
 * @property kubernetesClient the [KubernetesClient] to use to stop the messaging application
 */
@Singleton
internal class StopCommand(private val kubernetesClient: KubernetesClient) :
    CliktCommand(name = "stop", help = "Stop messaging application(s) running in Kubernetes") {

  private val names: Set<String> by
      argument(help = "the names of the messaging application(s) to stop").multiple().unique()

  override fun run() {
    with(MessagingApplicationClient.new(kubernetesClient)) { for (name in names) delete(name) }
  }
}
