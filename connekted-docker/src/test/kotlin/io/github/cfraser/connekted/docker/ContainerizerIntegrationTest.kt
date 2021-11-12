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
package io.github.cfraser.connekted.docker

import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag

/**
 * Integration test for [Containerizer].
 *
 * To successfully run this test `docker` must be installed and the credentials for a docker
 * repository (`DOCKER_REPOSITORY`) must be accessible (through `DOCKER_USERNAME` and
 * `DOCKER_PASSWORD` environment variables).
 */
@Tag("integration")
internal class ContainerizerIntegrationTest {

  @Test
  fun `Verify that containerized JAR runs as expected`() {
    val jar = File(checkNotNull(this::class.java.classLoader.getResource("hello-world.jar")?.file))

    val image = runBlocking { containerizer.containerizeJar(jar.toPath(), null, emptyList()) }
    assertTrue { image.isNotBlank() }
    assertEquals("Hello world", runProcess("docker", "run", image))
  }

  @Test
  fun `Verify that tarred images in zip are persisted`() {
    val zip = File(checkNotNull(this::class.java.classLoader.getResource("images.zip")?.file))

    val imageReferences = runBlocking { containerizer.containerizeImageZip(zip.toPath()) }
    assertTrue { imageReferences.isNotEmpty() }
  }

  companion object {

    private val containerizer =
        Containerizer.new(
            ContainerDestination(
                System.getenv("DOCKER_REPOSITORY"),
                System.getenv("DOCKER_USERNAME"),
                System.getenv("DOCKER_PASSWORD")))

    /**
     * Run the [command] as a process and return the output.
     *
     * @param command the command to run
     * @return the process output
     */
    private fun runProcess(vararg command: String): String {
      val processBuilder = ProcessBuilder(*command)
      val process = processBuilder.start()

      process.inputStream.bufferedReader().use { reader ->
        val output = reader.readText()
        if (process.waitFor() != 0) {
          val stderr = InputStreamReader(process.errorStream, StandardCharsets.UTF_8).readText()
          error("Command '${command.joinToString()}' failed: $stderr")
        }
        return output
      }
    }
  }
}
