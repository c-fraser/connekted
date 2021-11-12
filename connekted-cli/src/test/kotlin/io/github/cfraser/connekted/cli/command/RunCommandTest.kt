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
import io.github.cfraser.connekted.cli.config.DockerConfig
import io.github.cfraser.connekted.k8s.MessagingApplication
import io.github.cfraser.connekted.k8s.client.MessagingApplicationClient
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer
import java.nio.file.Files
import javax.inject.Inject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

@QuarkusTest
@WithKubernetesTestServer
internal class RunCommandTest {

  @Inject private lateinit var runCommand: RunCommand

  @Inject private lateinit var kubernetesClient: KubernetesClient

  private val messagingApplicationClient by lazy {
    MessagingApplicationClient.new(kubernetesClient)
  }

  @BeforeTest
  fun beforeTest() {
    mockkObject(DockerConfig.Companion)
    coEvery { DockerConfig.retrieve(any()) } returns
        DockerConfig("https://index.docker.io/v1/", "repo/connekted", "test-user", "test-password")

    kubernetesClient
        .apiextensions()
        .v1()
        .customResourceDefinitions()
        .createOrReplace(MessagingApplication.customResourceDefinition)
  }

  @AfterTest
  fun afterTest() {
    unmockkAll()
  }

  @Test
  fun `Verify the run command`() {
    val name = "test-messaging-application"
    runCommand.parse(listOf("${Files.createTempFile(null, null)}", "-n", name), null)
    assertNotNull(messagingApplicationClient.get(name))
  }
}
