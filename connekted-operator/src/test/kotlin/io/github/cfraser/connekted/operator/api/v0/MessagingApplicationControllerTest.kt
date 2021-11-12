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
package io.github.cfraser.connekted.operator.api.v0

import io.github.cfraser.connekted.common.MessagingApplicationData
import io.github.cfraser.connekted.common.MessagingComponentData
import io.github.cfraser.connekted.common.MessagingData
import io.github.cfraser.connekted.operator.registry.MessagingApplicationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient

@ExtendWith(SpringExtension::class)
@WebFluxTest(
    MessagingApplicationController::class,
    excludeAutoConfiguration = [ReactiveSecurityAutoConfiguration::class])
@Import(MessagingApplicationRegistry::class)
class MessagingApplicationControllerTest {

  @MockBean private lateinit var messagingApplicationRegistry: MessagingApplicationRegistry

  @Autowired private lateinit var webClient: WebTestClient

  @Value("\${connekted.v0.api.path}") private lateinit var v0ApiPath: String

  @Test
  fun `Verify retrieving messaging applications`() {
    val msgAppData = (0..3).map { messagingApplicationData(it) }.toSet()
    Mockito.`when`(messagingApplicationRegistry.getAll()).thenReturn(msgAppData)

    val responseBody =
        webClient
            .get()
            .uri("$v0ApiPath/messaging-application")
            .exchange()
            .expectStatus()
            .is2xxSuccessful
            .expectBody(object : ParameterizedTypeReference<Set<MessagingApplicationData>>() {})
            .returnResult()
            .responseBody
            ?: fail("get all messaging applications returned `null` response body")

    Mockito.verify(messagingApplicationRegistry, Mockito.times(1)).getAll()
    assertEquals(msgAppData, responseBody)
  }

  @Test
  fun `Verify retrieving a messaging application by name`() {
    val msgAppData = messagingApplicationData(0)
    Mockito.`when`(messagingApplicationRegistry["messaging-application-0"]).thenReturn(msgAppData)

    val responseBody =
        webClient
            .get()
            .uri("$v0ApiPath/messaging-application/{name}", "messaging-application-0")
            .exchange()
            .expectStatus()
            .is2xxSuccessful
            .expectBody(MessagingApplicationData::class.java)
            .returnResult()
            .responseBody
            ?: fail("get messaging application returned `null` response body")

    Mockito.verify(messagingApplicationRegistry, Mockito.times(1))["messaging-application-0"]
    assertEquals(msgAppData, responseBody)
  }

  companion object {

    private fun messagingApplicationData(i: Int) =
        MessagingApplicationData(
            "messaging-application-$i",
            listOf(
                MessagingComponentData(
                    "messaging-component-$i",
                    MessagingComponentData.Type.SENDER,
                    MessagingData(
                        sendTo = "receiver-$i",
                        sent = 0,
                        sendErrors = 0,
                        received = 0,
                        receiveErrors = 0))),
            sent = 0,
            sendErrors = 0,
            received = 0,
            receiveErrors = 0)
  }
}
