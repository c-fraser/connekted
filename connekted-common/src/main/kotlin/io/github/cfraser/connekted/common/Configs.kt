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
package io.github.cfraser.connekted.common

/** The [Configs] object consolidates configuration shared amongst `connekted` modules. */
object Configs {

  /** The name of the environment variable for the messaging application name. */
  const val nameEnv = "CONNEKTED_NAME"

  /** The name of the environment variable for the authorized username used by `connekted`. */
  const val usernameEnv = "CONNEKTED_USERNAME"

  /**
   * The name of the environment variable for the password corresponding to the `connekted`
   * username.
   */
  const val passwordEnv = "CONNEKTED_PASSWORD"

  /** The port the `connekted` (HTTP) applications bind to. */
  const val appPort = 8080

  /**
   * The name of the environment variable for the messaging token used by sender(s) and receiver(s)
   * to authenticate.
   */
  const val messagingTokenEnv = "CONNEKTED_MESSAGING_TOKEN"

  /** The path the liveness probe makes a request to. */
  const val livenessPath = "/healthz"

  /** The path the readiness probe makes a request to. */
  const val readinessPath = "/readyz"

  /** The path the to retrieve metrics from a `connekted` app. */
  const val metricsPath = "/metrics"

  /**
   * The name of the `connekted` [HOCON](https://github.com/lightbend/config/blob/master/HOCON.md)
   * configuration file.
   */
  const val configFile = "connekted.conf"

  /** The mount path for the [configFile]. */
  const val configMountPath = "/etc/connekted"

  /** The name of the messaging application keystore file. */
  const val keystoreFile = "keystore.jks"

  /** The path containing the value for the logging level for `connekted` resources. */
  const val loggingLevelPath = "connekted.logging.level"

  /** The name of the *messaging application operator* resources. */
  const val operatorName = "connekted-operator"

  /** The name of the configmap containing the `connekted` image references. */
  const val dockerImagesConfig = "connekted-docker-images"

  /**
   * The name of the secret containing credentials to access the docker registry/repository with
   * messaging application images.
   */
  const val dockerCredentialsName = "connekted-docker-registry-config"

  /** The name of the basic auth secret used by `connekted` resources to authenticate. */
  const val authName = "connekted-basic-auth"

  /** The username of the internal `connekted` administrator. */
  const val adminUsername = "connekted-admin"

  /** The key to access the username from the `connekted` auth secret. */
  const val usernameKey = "username"

  /** The key to access the password from the `connekted` auth secret. */
  const val passwordKey = "password"

  /** The name of the token secret used by messaging components to authenticate. */
  const val messagingTokenSecretName = "connekted-messaging-token"

  /** The key to access the messaging token from the corresponding secret. */
  const val messagingTokenKey = "token"
}
