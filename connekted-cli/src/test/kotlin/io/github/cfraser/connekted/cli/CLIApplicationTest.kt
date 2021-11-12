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
package io.github.cfraser.connekted.cli

import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer
import javax.inject.Inject
import kotlin.test.Test
import kotlin.test.assertEquals

@QuarkusTest
@WithKubernetesTestServer
internal class CLIApplicationTest {

  @Inject private lateinit var cliApplication: CLIApplication

  @Test
  fun `Verify root command initialization`() {
    var statusCode = cliApplication.run("--help")
    assertEquals(0, statusCode)
    statusCode = cliApplication.run("deploy", "--help")
    assertEquals(0, statusCode)
    statusCode = cliApplication.run("get", "--help")
    assertEquals(0, statusCode)
    statusCode = cliApplication.run("run", "--help")
    assertEquals(0, statusCode)
    statusCode = cliApplication.run("stop", "--help")
    assertEquals(0, statusCode)
    statusCode = cliApplication.run("teardown", "--help")
    assertEquals(0, statusCode)
    statusCode = cliApplication.run("watch", "--help")
    assertEquals(0, statusCode)
  }
}
