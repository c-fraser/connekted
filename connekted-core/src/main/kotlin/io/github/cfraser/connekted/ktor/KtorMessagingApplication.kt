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
package io.github.cfraser.connekted.ktor

import io.github.cfraser.connekt.api.Transport
import io.github.cfraser.connekted.Envs
import io.github.cfraser.connekted.MessagingApplication
import io.github.cfraser.connekted.MessagingComponent
import io.github.cfraser.connekted.Metrics
import io.github.cfraser.connekted.Receiver
import io.github.cfraser.connekted.Sender
import io.github.cfraser.connekted.SendingReceiver
import io.github.cfraser.connekted.common.Configs
import io.github.cfraser.connekted.common.MessagingApplicationData
import io.github.cfraser.connekted.common.MessagingComponentData
import io.github.cfraser.connekted.common.MessagingData
import io.github.cfraser.connekted.ktor.KtorMessagingApplication.ApplicationEngineInitializer
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.basic
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.netty.Netty
import io.micrometer.prometheus.PrometheusMeterRegistry
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.slf4j.event.Level

/**
 * The [KtorMessagingApplication] is the [MessagingApplication] implementation that runs an
 * [embeddedServer], adjacent to the [messagingComponents], which exposes endpoints for management
 * operations.
 *
 * @property name the name of the messaging application
 * @property messagingComponents the [MessagingComponent] instances to manage
 * @property transport the [Transport] used by the [messagingComponents] to send and receive
 * messages
 * @property onClose the function to invoke when the messaging application is shutdown
 * @param applicationEngineInitializer the [KtorMessagingApplication.ApplicationEngineInitializer]
 */
internal class KtorMessagingApplication(
    override val name: String,
    override val messagingComponents: Collection<MessagingComponent>,
    private val transport: Transport,
    private val onClose: () -> Unit = {},
    applicationEngineInitializer: ApplicationEngineInitializer? = null,
) : MessagingApplication {

  private val running = atomic(false)

  /**
   * Initialize the embedded server using the
   * [KtorMessagingApplication.ApplicationEngineInitializer].
   *
   * The [Application] exposes observability endpoints which are necessary to determine the state of
   * the application (running within a Kubernetes pod), additionally, other generically useful
   * [io.ktor.application.ApplicationFeature] objects are installed. This includes configuring the
   * [basic] authentication with the [Envs.username] and [Envs.password] as the only valid
   * credentials.
   */
  private val applicationEngine: ApplicationEngine by lazy {
    (applicationEngineInitializer
        ?: nettyApplicationInitializer) {
      install(Authentication) {
        basic {
          charset = Charsets.UTF_8
          validate { credentials ->
            credentials.takeIf { it.name == Envs.username && it.password == Envs.password }?.run {
              UserIdPrincipal(name)
            }
          }
        }
      }

      install(MicrometerMetrics) { registry = Metrics.meterRegistry }
      install(ContentNegotiation) { jackson() }
      install(DefaultHeaders)
      install(CallLogging) { level = Level.DEBUG }

      routing {
        // Liveness health check endpoint
        get(Configs.livenessPath) { call.respond(HttpStatusCode.OK) }

        // Readiness health check endpoint
        get(Configs.readinessPath) { call.respond(HttpStatusCode.OK) }

        // If possible, expose the metrics endpoint for prometheus to scrape
        Metrics.meterRegistry.run {
          if (this is PrometheusMeterRegistry) get(Configs.metricsPath) { call.respond(scrape()) }
        }

        authenticate {
          // Shutdown the messaging application endpoint
          post("/shutdown/") { call.respond(HttpStatusCode.OK).also { shutdown() } }

          // Retrieve the messaging application data endpoint
          get("/data/") { call.respond(data()) }
        }
      }
    }
  }

  /**
   * Run the [MessagingApplication].
   *
   * This is a **blocking** function which starts the [messagingComponents] component and then the
   * [applicationEngine]. The invocation of [ApplicationEngine.start] with `wait = true` is what
   * causes this function not to return (until the app engine stops and exits).
   */
  override fun start() {
    if (running.compareAndSet(expect = false, update = true)) {
      logger.info { "starting messaging application $name" }
      runBlocking(Dispatchers.IO) {
        for (component in messagingComponents) component.run { start() }
        applicationEngine.start(wait = true)
      }
    }
  }

  /**
   * Close the [MessagingApplication].
   *
   * Shutdown the [MessagingComponent] component then stop the [applicationEngine].
   */
  override fun shutdown() {
    if (running.compareAndSet(expect = true, update = false)) {
      logger.info { "Closing messaging application $name" }
      runBlocking(Dispatchers.IO) {
        @OptIn(ExperimentalTime::class)
        withTimeout(Duration.seconds(10)) {
          for (component in messagingComponents) component.shutdown()
        }
      }
      runCatching(onClose).onFailure { logger.error(it) { "Failed to execute provided `onClose`" } }
      applicationEngine
          .runCatching { stop(gracePeriod = 10, timeout = 30, timeUnit = TimeUnit.SECONDS) }
          .onFailure { logger.error(it) { "Failed to stop application engine" } }
      runCatching(transport::close).onFailure { logger.error(it) { "Failed to close transport" } }
    }
  }

  /** Construct the [MessagingComponentData] for `this` [KtorMessagingApplication]. */
  internal suspend fun data() =
      withContext(Dispatchers.Default) {
        val messagingComponentData =
            messagingComponents.mapNotNull { messagingComponent ->
              when (messagingComponent) {
                is Sender<*> ->
                    MessagingComponentData(
                        messagingComponent.name,
                        MessagingComponentData.Type.SENDER,
                        messagingComponent.data())
                is Receiver<*> ->
                    MessagingComponentData(
                        messagingComponent.name,
                        MessagingComponentData.Type.RECEIVER,
                        messagingComponent.data())
                is SendingReceiver<*, *> ->
                    MessagingComponentData(
                        messagingComponent.name,
                        MessagingComponentData.Type.SENDING_RECEIVER,
                        messagingComponent.run {
                          val sendData = data()
                          val receiveData = data()
                          MessagingData(
                              sendTo = sendData.sendTo,
                              sent = sendData.sent,
                              received = receiveData.received,
                              sendErrors = sendData.sendErrors,
                              receiveErrors = receiveData.receiveErrors)
                        },
                    )
                else -> {
                  logger.error {
                    "Unexpected messaging component type ${messagingComponent::class.simpleName}"
                  }
                  null
                }
              }
            }

        transport.metrics().run {
          MessagingApplicationData(
              name,
              messagingComponentData,
              sent = messagesSent,
              sendErrors = sendErrors,
              received = messagesReceived,
              receiveErrors = receiveErrors)
        }
      }

  /**
   * The [KtorMessagingApplication.ApplicationEngineInitializer] type represents a function that
   * initializes an [ApplicationEngine].
   */
  internal fun interface ApplicationEngineInitializer {

    /**
     * Initialize an [ApplicationEngine] with the [applicationConfigurer].
     *
     * @param applicationConfigurer the function to use to configure the [Application]
     * @return the [ApplicationEngine]
     */
    operator fun invoke(applicationConfigurer: Application.() -> Unit): ApplicationEngine
  }

  private companion object {

    /**
     * An [KtorMessagingApplication.ApplicationEngineInitializer] implementation which initializes a
     * HTTP server which binds to the [Configs.appPort] and configures authentication with the
     * internal `connekted` credentials, i.e. [Envs.username] and [Envs.password].
     */
    val nettyApplicationInitializer = ApplicationEngineInitializer { configurer ->
      embeddedServer(Netty, port = Configs.appPort) { configurer() }
    }

    val logger = KotlinLogging.logger {}
  }
}
