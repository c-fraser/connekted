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
package io.github.cfraser.connekted.cli.config

import io.fabric8.kubernetes.client.KubernetesClient
import io.github.cfraser.connekted.common.Configs
import java.util.Base64
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.inject.Produces
import org.eclipse.microprofile.rest.client.RestClientBuilder
import org.jboss.resteasy.client.jaxrs.internal.BasicAuthentication

/** The [RestClientConfig] class configures the [RestClientBuilder] for the application. */
@ApplicationScoped
internal class RestClientConfig {

  /**
   * Lazily initialize a [RestClientBuilder].
   *
   * Configure the [BasicAuthentication] using the `username` and `password` on the
   * [Configs.authName] secret.
   *
   * @param kubernetesClient the [KubernetesClient] to use to get the basic auth secret
   * @return the [RestClientBuilder]
   */
  @Produces
  fun restClientBuilder(kubernetesClient: KubernetesClient): Lazy<RestClientBuilder> {
    return lazy {
      val basicAuthentication =
          with(kubernetesClient.secrets().withName(Configs.authName).require()) {
            fun String.decodeBase64() = String(Base64.getDecoder().decode(this))
            val username = checkNotNull(data[Configs.usernameKey]).decodeBase64()
            val password = checkNotNull(data[Configs.passwordKey]).decodeBase64()
            BasicAuthentication(username, password)
          }

      RestClientBuilder.newBuilder().register(basicAuthentication)
    }
  }
}
