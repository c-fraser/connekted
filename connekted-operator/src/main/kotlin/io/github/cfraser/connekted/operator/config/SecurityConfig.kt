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
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain

/** The [SecurityConfig] class configures authentication and authorization for the application. */
@EnableWebFluxSecurity
internal class SecurityConfig {

  /**
   * Initialize the [PasswordEncoder] to use to encode passwords stored in the [userDetailsService].
   *
   * @return the [PasswordEncoder]
   */
  @Bean
  fun passwordEncoder(): PasswordEncoder {
    return BCryptPasswordEncoder()
  }

  /**
   * The in-memory [ReactiveUserDetailsService] which stores and provides access to the authorized
   * users.
   *
   * The [Configs.usernameEnv] and [Configs.passwordEnv] environment variables must be set to
   * initialize the [MapReactiveUserDetailsService] with the internal `ADMIN` user.
   *
   * @param adminUsername the username of the internal administrator
   * @param adminPassword the password for the [adminUsername]
   * @param passwordEncoder the [PasswordEncoder] used to encode passwords
   * @return the [ReactiveUserDetailsService]
   */
  @Bean
  fun userDetailsService(
      @Value("\${CONNEKTED_USERNAME}") adminUsername: String,
      @Value("\${CONNEKTED_PASSWORD}") adminPassword: String,
      passwordEncoder: PasswordEncoder
  ): ReactiveUserDetailsService {
    val internalUser =
        User.withUsername(adminUsername)
            .password(passwordEncoder.encode(adminPassword))
            .roles("ADMIN")
            .build()
    return MapReactiveUserDetailsService(internalUser)
  }

  /**
   * IInitialize the [SecurityWebFilterChain] which restricts access to the [v0ApiPath] to the
   * authorized users via basic authentication.
   *
   * @param v0ApiPath the v0 API path prefix
   * @param http the [ServerHttpSecurity] to configure and build
   * @return the [SecurityWebFilterChain]
   */
  @Bean
  fun securityWebFilterChain(
      @Value("\${connekted.v0.api.path}") v0ApiPath: String,
      http: ServerHttpSecurity
  ): SecurityWebFilterChain {
    return http.csrf()
        .disable()
        .authorizeExchange()
        .pathMatchers(v0ApiPath)
        .hasRole("ADMIN")
        .pathMatchers("/**")
        .permitAll()
        .and()
        .httpBasic()
        .and()
        .build()
  }
}
