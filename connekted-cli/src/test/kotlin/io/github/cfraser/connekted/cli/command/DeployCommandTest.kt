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
import io.github.cfraser.connekted.common.Configs
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer
import java.nio.file.Files
import javax.inject.Inject
import kotlin.test.Test
import org.junit.jupiter.api.assertDoesNotThrow

@QuarkusTest
@WithKubernetesTestServer
internal class DeployCommandTest {

  @Inject private lateinit var deployCommand: DeployCommand

  @Inject private lateinit var kubernetesClient: KubernetesClient

  @Test
  fun `Verify the deploy command`() {
    deployCommand.parse(
        listOf(
            "${Files.createTempFile(null, null)}",
            "--docker-server",
            "https://index.docker.io/v1/",
            "--docker-repository",
            "repo/connekted",
            "--docker-username",
            "test-user",
            "--docker-password",
            "test-password",
            "--continue"),
        null)

    assertDoesNotThrow {
      kubernetesClient.apps().deployments().withName(Configs.operatorName).require()
    }
  }
}
