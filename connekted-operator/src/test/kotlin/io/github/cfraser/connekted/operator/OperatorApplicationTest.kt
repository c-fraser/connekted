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
package io.github.cfraser.connekted.operator

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer
import io.fabric8.mockwebserver.Context
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.k8s.MessagingApplication
import io.github.cfraser.connekted.operator.config.OperatorConfig
import io.github.cfraser.connekted.operator.config.RegistryConfig
import io.github.cfraser.connekted.operator.registry.MessagingApplicationRegistry
import io.javaoperatorsdk.operator.Operator
import kotlin.test.Test
import kotlin.test.assertNotNull
import okhttp3.mockwebserver.MockWebServer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@Import(OperatorApplicationTest.TestConfig::class, OperatorConfig::class, RegistryConfig::class)
@TestPropertySource(
    properties =
        [
            "CONNEKTED_USERNAME=${Configs.adminUsername}",
            "CONNEKTED_PASSWORD=test-password",
            "spring.main.allow-bean-definition-overriding=true"])
internal class OperatorApplicationTest {

  @Autowired private lateinit var operator: Operator
  @Autowired private lateinit var messagingApplicationRegistry: MessagingApplicationRegistry

  @Test
  fun `Verify application initializes successfully`() {
    assertNotNull(operator)
    assertNotNull(messagingApplicationRegistry)
  }

  @Configuration
  class TestConfig {

    init {
      // `io.javaoperatorsdk.operator.Operator.start()` throws an exception if the current namespace
      // cannot be inferred from the client configuration
      System.setProperty(Config.KUBERNETES_NAMESPACE_SYSTEM_PROPERTY, "default")
    }

    @Bean fun mockWebServer() = MockWebServer()

    @Bean
    fun kubernetesMockServer(mockWebServer: MockWebServer) =
        KubernetesMockServer(
                Context(),
                mockWebServer,
                mutableMapOf(),
                KubernetesCrudDispatcher(emptyList()),
                true)
            .apply { init() }

    @Bean
    @Primary
    fun kubernetesClient(kubernetesMockServer: KubernetesMockServer): KubernetesClient =
        kubernetesMockServer.createClient().also { kubernetesClient ->
          kubernetesClient
              .apiextensions()
              .v1()
              .customResourceDefinitions()
              .create(MessagingApplication.customResourceDefinition)
        }
  }
}
