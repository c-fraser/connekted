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
@file:OptIn(ExperimentalTypeInference::class)

package io.github.cfraser.connekted

import io.github.cfraser.connekt.api.Transport
import io.github.cfraser.connekted.ktor.KtorMessagingApplication
import java.util.function.Consumer
import kotlin.experimental.ExperimentalTypeInference

/**
 * [MessagingApplication] is the process that manages the execution of a [MessagingComponent]
 * instances.
 *
 * The [start] implementation is a **blocking** function which starts the [messagingComponents]
 * which continue running until a shutdown is performed.
 */
interface MessagingApplication {

  /** The name of the [MessagingApplication]. */
  val name: String

  /**
   * The [MessagingComponent] instances to manage.
   *
   * The [messagingComponents] are started in [start] and stopped in [shutdown].
   */
  val messagingComponents: Collection<MessagingComponent>

  /** Start the [MessagingApplication] and **block** until it is [shutdown]. */
  fun start()

  /** Shutdown the [MessagingApplication]. */
  fun shutdown()

  /**
   * [MessagingApplication.Builder] builds a [MessagingApplication], which encapsulates (manages the
   * execution of) [MessagingComponent] instances.
   *
   * @property transport the [Transport] to use to send and receive messages
   */
  @Connekted.Dsl
  class Builder internal constructor(private val transport: Transport) {

    /**
     * The [MessagingComponent] instances defining the messaging capabilities of the resulting
     * [MessagingApplication].
     */
    private val messagingComponents = mutableListOf<MessagingComponent>()

    private var close: () -> Unit = {}

    /** The [Config] available for use by the messaging implementation(s). */
    @Suppress("unused") val config: Config = Config.initialize()

    /**
     * Initialize a **sender** [MessagingComponent] then add it to the [messagingComponents].
     *
     * Constructs a [Sender.Builder] then applies the [initializer] to
     * [MessagingComponent.Builder.build] the [MessagingComponent].
     *
     * @param T the type to send
     * @param initializer the [Sender.Builder] initialization block
     * @return the [MessagingComponent] for the sender
     * @see Sender.Builder for more information about building a sender [MessagingComponent]
     */
    fun <T> addSender(@BuilderInference initializer: Sender.Builder<T>.() -> Unit) {
      add(Sender.Builder<T>().apply(initializer).build(transport))
    }

    /**
     * Initialize a **sender** [MessagingComponent] then add it to the [messagingComponents].
     *
     * This function uses [Consumer] so that a **sender** [MessagingComponent] can be initialized
     * idiomatically in *Java* code. *Kotlin* users should consider/prefer using the [addSender]
     * function.
     *
     * @param T the type to send
     * @param initializer the [Consumer] of [Sender.Builder]
     * @see addSender
     */
    fun <T> addSender(initializer: Consumer<Sender.Builder<T>>) {
      addSender(initializer::accept)
    }

    /**
     * Initialize a **receiver** [MessagingComponent] then add it to the [messagingComponents].
     *
     * Constructs a [Receiver.Builder] then applies the [initializer] to
     * [MessagingComponent.Builder.build] the [MessagingComponent].
     *
     * @param T the type to receive
     * @param initializer the [Receiver.Builder] initialization block
     * @return the [MessagingComponent] for the receiver
     * @see Receiver.Builder for more information about building a receiver [MessagingComponent]
     */
    fun <T> addReceiver(@BuilderInference initializer: Receiver.Builder<T>.() -> Unit) {
      add(Receiver.Builder<T>().apply(initializer).build(transport))
    }

    /**
     * Initialize a **receiver** [MessagingComponent] then add it to the [messagingComponents].
     *
     * This function uses [Consumer] so that a **receiver** [MessagingComponent] can be initialized
     * idiomatically in *Java* code. *Kotlin* users should consider/prefer using the [addReceiver]
     * function.
     *
     * @param T the type to receive
     * @param initializer the [Consumer] of [Receiver.Builder]
     * @see addReceiver
     */
    fun <T> addReceiver(initializer: Consumer<Receiver.Builder<T>>) {
      addReceiver(initializer::accept)
    }

    /**
     * Initialize a **sending-receiver** [MessagingComponent] then add it to the
     * [messagingComponents] .
     *
     * Constructs a [SendingReceiver.Builder] then applies the [initializer] to
     * [MessagingComponent.Builder.build] the [MessagingComponent].
     *
     * @param In the type to receive
     * @param Out the type to send
     * @param initializer the [SendingReceiver.Builder] initialization block
     * @return the [MessagingComponent] for the sending-receiver
     * @see SendingReceiver.Builder for more information about building a sending-receiver
     * [MessagingComponent]
     */
    fun <In, Out> addSendingReceiver(
        @BuilderInference initializer: SendingReceiver.Builder<In, Out>.() -> Unit
    ) {
      add(SendingReceiver.Builder<In, Out>().apply(initializer).build(transport))
    }

    /**
     * Initialize a **sending-receiver** [MessagingComponent] then add it to the
     * [messagingComponents] .
     *
     * This function uses [Consumer] so that a **sending-receiver** [MessagingComponent] can be
     * initialized idiomatically in *Java* code. *Kotlin* users should consider/prefer using the
     * [addSendingReceiver] function.
     *
     * @param In the type to receive
     * @param Out the type to send
     * @param initializer the [Consumer] of [SendingReceiver.Builder]
     * @see addSendingReceiver
     */
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun <In, Out> addSendingReceiver(initializer: Consumer<SendingReceiver.Builder<In, Out>>) {
      addSendingReceiver(initializer::accept)
    }

    /**
     * Use the [block] to [close] the [MessagingApplication].
     *
     * @param block the function to invoke when shutting down the messaging application
     */
    fun onClose(block: () -> Unit) {
      this.close = block
    }

    /**
     * The [KtorMessagingApplication.ApplicationEngineInitializer] to use to initialize the
     * [io.ktor.server.engine.ApplicationEngine] for the [KtorMessagingApplication].
     */
    internal var applicationEngineInitializer:
        KtorMessagingApplication.ApplicationEngineInitializer? =
        null

    /**
     * Build the [MessagingApplication].
     *
     * @return the messaging application
     */
    internal fun build(): MessagingApplication {
      return KtorMessagingApplication(
          Envs.name,
          messagingComponents,
          transport,
          {
            close()
            transport.close()
          },
          applicationEngineInitializer)
    }

    /** Add the [messagingComponent] to the [messagingComponents]. */
    private fun add(messagingComponent: MessagingComponent) {
      run messagingComponent@{
        check(messagingComponents.none { messagingComponent.name == it.name }) {
          "each messaging component must have a unique name"
        }

        messagingComponents += messagingComponent
      }
    }
  }
}
