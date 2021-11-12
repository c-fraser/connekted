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

import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.ResourceNotFoundException
import io.github.cfraser.connekted.cli.config.DockerConfig
import io.github.cfraser.connekted.cli.config.DockerConfig.Companion.toDockerRegistryConfig
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.deploy.DeploymentManager
import io.github.cfraser.connekted.k8s.client.MessagingApplicationClient
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import org.junit.jupiter.api.assertThrows

@QuarkusTest
@WithKubernetesTestServer
internal class TeardownCommandTest {

  @Inject private lateinit var teardownCommand: TeardownCommand

  @Inject private lateinit var kubernetesClient: KubernetesClient

  private val messagingApplicationClient by lazy {
    MessagingApplicationClient.new(kubernetesClient)
  }

  @BeforeTest
  fun beforeTest() {
    val dockerConfig =
        DockerConfig("https://index.docker.io/v1/", "repo/connekted", "test-user", "test-password")

    mockkObject(DockerConfig)
    coEvery { DockerConfig.retrieve(any()) } returns dockerConfig

    DeploymentManager.new(
            kubernetesClient,
            dockerConfig.toDockerRegistryConfig(
                setOf("repo/connekted:connekted-operator-0.0.0-12345")))
        .deploy()
    messagingApplicationClient.createOrReplace(
        name, "repo/connekted:messaging-application-0.0.0-12345", null)
  }

  @AfterTest
  fun afterTest() {
    unmockkAll()
  }

  @Test
  fun `Verify the teardown command`() {
    teardownCommand.parse(emptyList(), null)
    assertNull(messagingApplicationClient.get(name))
    assertThrows<ResourceNotFoundException> {
      kubernetesClient.apps().deployments().withName(Configs.operatorName).require()
    }
  }

  companion object {

    private const val name = "test-messaging-application"
  }
}
