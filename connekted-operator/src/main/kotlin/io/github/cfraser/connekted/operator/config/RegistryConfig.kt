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
package io.github.cfraser.connekted.operator.config

import io.github.cfraser.connekted.common.Configs
import io.netty.handler.logging.LogLevel
import java.nio.charset.StandardCharsets
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.transport.logging.AdvancedByteBufFormat

/**
 * The [RegistryConfig] class configures the
 * [io.github.cfraser.connekted.operator.registry.MessagingApplicationRegistry] and
 * [io.github.cfraser.connekted.operator.registry.MessagingComponentRegistry] for the application.
 */
@Configuration
@ComponentScan("io.github.cfraser.connekted.operator.registry")
internal class RegistryConfig {

  /**
   * Initialize an [HttpClient] with [AdvancedByteBufFormat.TEXTUAL] logging of requests and
   * responses.
   *
   * @return the [HttpClient]
   */
  @Bean
  fun httpClient(): HttpClient {
    return HttpClient.create()
        .wiretap(
            "reactor.netty.http.client.HttpClient", LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL)
  }

  /**
   * Initialize a [WebClient] that configures basic authentication with the [username] and
   * [password] of the internal administrator.
   *
   * The [Configs.usernameEnv] and [Configs.passwordEnv] environment variables must be set to create
   * the [WebClient].
   *
   * @param httpClient the [HttpClient] to make HTTP connections with
   * @param username the authorized username
   * @param password the password for the username
   * @return the [WebClient]
   */
  @Bean
  fun webClient(
      httpClient: HttpClient,
      @Value("\${CONNEKTED_USERNAME}") username: String,
      @Value("\${CONNEKTED_PASSWORD}") password: String,
  ): WebClient {
    return WebClient.builder()
        .clientConnector(ReactorClientHttpConnector(httpClient))
        .defaultHeaders { headers ->
          headers.setBasicAuth(username, password, StandardCharsets.UTF_8)
        }
        .build()
  }
}
