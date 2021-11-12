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
import io.github.cfraser.connekted.k8s.MessagingApplication
import io.github.cfraser.connekted.k8s.client.MessagingApplicationClient
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer
import javax.inject.Inject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull

@QuarkusTest
@WithKubernetesTestServer
internal class StopCommandTest {

  @Inject private lateinit var stopCommand: StopCommand

  @Inject private lateinit var kubernetesClient: KubernetesClient

  private val messagingApplicationClient by lazy {
    MessagingApplicationClient.new(kubernetesClient)
  }

  @BeforeTest
  fun beforeTest() {
    kubernetesClient
        .apiextensions()
        .v1()
        .customResourceDefinitions()
        .createOrReplace(MessagingApplication.customResourceDefinition)
    messagingApplicationClient.createOrReplace(
        name, "repo/connekted:messaging-application-0.0.0-12345", null)
  }

  @Test
  fun `Verify the stop command`() {
    stopCommand.parse(listOf(name), null)
    assertNull(messagingApplicationClient.get(name))
  }

  companion object {

    private const val name = "test-messaging-application"
  }
}
