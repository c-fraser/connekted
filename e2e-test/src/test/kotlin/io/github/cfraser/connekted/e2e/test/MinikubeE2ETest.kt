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
package io.github.cfraser.connekted.e2e.test

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.apache.commons.lang3.StringUtils
import org.junit.jupiter.api.Tag
import org.zeroturnaround.exec.ProcessExecutor

/**
 * E2E/integration test for `connekted`.
 *
 * To successfully run this test, [minikube](https://minikube.sigs.k8s.io/docs/start/) must be
 * installed, and the credentials for a docker repository (`DOCKER_REPOSITORY`) must be accessible
 * (through `DOCKER_USERNAME` and `DOCKER_PASSWORD` environment variables).
 */
@Tag("e2e")
class MinikubeE2ETest {

  @BeforeTest
  fun beforeTest() {
    runCommand("minikube", "start")
    runCommand(
        "kubectl",
        "apply",
        "-f",
        "https://raw.githubusercontent.com/nats-io/k8s/master/nats-server/single-server-nats.yml")
  }

  @AfterTest
  fun afterTest() {
    runCommand("minikube", "delete")
  }

  @Test
  fun e2eTest() {
    val e2eJar = resourceFile("e2e.jar")
    val cliJar = resourceFile("cli.jar")
    val imagesZip = resourceFile("images.zip")

    val dockerRepository = checkNotNull(System.getenv("DOCKER_REPOSITORY"))
    val dockerUsername = checkNotNull(System.getenv("DOCKER_USERNAME"))
    val dockerPassword = checkNotNull(System.getenv("DOCKER_PASSWORD"))

    fun runCliCommand(vararg command: String) = runCommand("java", "-jar", "$cliJar", *command)

    runCliCommand(
        "deploy", "$imagesZip", "-r", dockerRepository, "-u", dockerUsername, "-p", dockerPassword)

    val exampleReceiver = "example-receiver"
    runCliCommand(
        "run", "$e2eJar", "-n", exampleReceiver, "--system-property", "run.example=example-03")

    val exampleSendingReceiver = "example-sending-receiver"
    runCliCommand(
        "run",
        "$e2eJar",
        "-n",
        exampleSendingReceiver,
        "--system-property",
        "run.example=example-04")

    val exampleSender = "example-sender"
    runCliCommand(
        "run", "$e2eJar", "-n", exampleSender, "--system-property", "run.example=example-02")

    val exampleMessagingApplication = "example-messaging-application"
    runCliCommand(
        "run",
        "$e2eJar",
        "-n",
        exampleMessagingApplication,
        "--system-property",
        "run.example=example-05")

    val javaExampleMessagingApplication = "java-example-messaging-application"
    runCliCommand(
        "run",
        "$e2eJar",
        "-n",
        javaExampleMessagingApplication,
        "--system-property",
        "run.example=example-06")

    val output =
        runCliCommand(
            "watch",
            exampleSender,
            exampleReceiver,
            exampleMessagingApplication,
            javaExampleMessagingApplication,
            exampleSendingReceiver,
            "-s",
            "180")
    val lines = output.split(System.lineSeparator())

    fun parseAfterDelimiterFromLines(delimiter: String, messagingApplicationName: String? = null) =
        lines
            .asSequence()
            .filter { line -> messagingApplicationName?.let { line.startsWith(it) } ?: true }
            .filter { line -> delimiter in line }
            .map { line -> line.substringAfterLast(delimiter).trim() }
            .toList()

    fun parseIntegersFromLines(delimiter: String) =
        parseAfterDelimiterFromLines(delimiter).map { it.toInt() }

    val integersSent = parseIntegersFromLines("example-sender sending")
    val integersReceived = parseIntegersFromLines("example-receiver received")
    assertTrue(
        "Integers ${
            (integersSent - integersReceived).joinToString(" ")
          } were sent but not received") {
      integersReceived.containsAll(integersSent)
    }

    val randomStringsSent =
        parseAfterDelimiterFromLines("random-strings-sender sending", exampleMessagingApplication) +
            parseAfterDelimiterFromLines(
                "random-strings-sender sending", javaExampleMessagingApplication)
    val reversedRandomStringsReceived =
        parseAfterDelimiterFromLines("reversed-random-strings-receiver received")
    val randomStringsReceived = reversedRandomStringsReceived.map { StringUtils.reverse(it) }
    assertTrue(
        "Random strings ${
        (randomStringsSent - randomStringsReceived).joinToString(" ")
      } were sent but not received") {
      randomStringsSent.containsAll(randomStringsReceived)
    }

    runCliCommand("teardown")
  }

  companion object {

    /**
     * Create a [File] instance from the resource corresponding to the [name].
     *
     * If the resource doesn't exist or can't be loaded then an [IllegalStateException] is thrown.
     */
    private fun resourceFile(name: String) =
        File(checkNotNull(this::class.java.classLoader.getResource(name)?.file))

    /**
     * Run the [command].
     *
     * If the command was not run successfully, [org.zeroturnaround.exec.ProcessResult.exitValue] is
     * not zero, then an [IllegalStateException] is thrown.
     *
     * @param command the command to run
     * @return the process output as a UTF-8 [String]
     */
    private fun runCommand(vararg command: String) =
        with(ProcessExecutor().command(*command).readOutput(true).execute()) {
          check(exitValue == 0) {
            "Failed to run command '${command.joinToString(" ")}' ${outputUTF8()}"
          }
          outputUTF8()
        }
  }
}
