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
package io.github.cfraser.connekted

import io.github.cfraser.connekt.api.Deserializer
import io.github.cfraser.connekt.api.Serializer
import io.github.cfraser.connekt.api.Transport
import io.github.cfraser.connekted.common.MessagingData
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import java.util.function.Function
import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.reactivestreams.Publisher

/**
 * The [SendingReceiver] defines a [MessagingComponent] type that receives messages, via the
 * [io.github.cfraser.connekt.api.ReceiveChannel], and can send, via the [sendChannel], the result
 * of processing the received messages.
 *
 * A [SendingReceiver] primarily delegates to its internal [Receiver], which is possible by
 * converting the [GeneratingMessageHandler] to a [MessageHandler] via [toMessageHandler].
 *
 * @param In the type that is received
 * @param Out the type that is sent
 * @param name the name of the messaging component
 * @param onClose the function to invoke when the [SendingReceiver] is closed
 * @property receiveFrom the queue to receive from
 * @property sendTo the queue to send to
 * @param generatingMessageHandler the function that is invoked when a message is received, it
 * accepts an instance of [In] and generates [Out] instance(s)
 * @param deserializer the [Deserializer] to use to construct instances of type [In]
 * @param serializer the [Serializer] which converts instances of [Out] to bytes
 * @param transport the [Transport] to use to send and receive messages
 */
class SendingReceiver<In, Out>
internal constructor(
    override val name: String,
    onClose: () -> Unit,
    private val receiveFrom: String,
    private val sendTo: String,
    generatingMessageHandler: GeneratingMessageHandler<In, Out>,
    deserializer: Deserializer<In>,
    serializer: Serializer<Out>,
    transport: Transport
) : MessagingComponent {

  private val sendChannel by lazy { transport.sendTo(sendTo, serializer) }
  private val receiver =
      Receiver(
          name,
          onClose,
          receiveFrom,
          generatingMessageHandler.toMessageHandler(sendChannel),
          deserializer,
          transport)

  private val tag = Tag.of(SendingReceiver::class.simpleName!!.lowercase(), name)
  private val generatedCounter = Metrics.meterRegistry.counter("generated-messages", Tags.of(tag))
  private val sentCounter = Metrics.meterRegistry.counter("sent-messages", Tags.of(tag))

  override fun CoroutineScope.start() {
    receiver.apply { start() }
  }

  override suspend fun shutdown() {
    receiver.shutdown()
  }

  override suspend fun data() =
      withContext(Dispatchers.Default) {
        MessagingData(
            sendTo = sendTo,
            sent = sentCounter.count().toLong(),
            sendErrors = (generatedCounter.count() - sentCounter.count()).toLong(),
            receiveFrom = receiveFrom,
            received = receiver.receivedCounter.count().toLong(),
            receiveErrors =
                (receiver.receivedCounter.count() - receiver.handledCounter.count()).toLong())
      }

  /**
   * Convert a [GeneratingMessageHandler] to a [MessageHandler].
   *
   * The instantiated [MessageHandler] sends the messages generated by the receiver
   * [GeneratingMessageHandler].
   *
   * @param In the type that is received
   * @param Out the type that is sent
   * @param sendChannel the [SendChannel] which handles the transport of the messages
   * @return the message handler
   */
  private fun <In, Out> GeneratingMessageHandler<In, Out>.toMessageHandler(
      sendChannel: SendChannel<Out>
  ) =
      MessageHandler<In> { `in` ->
        runCatching { handle(`in`) }
            .onFailure {
              logger.error(it) { "${this@SendingReceiver} failed to generate messages" }
            }
            .getOrNull()
            ?.onEach { generatedCounter.increment() }
            ?.runCatching {
              collect { `out` ->
                sendChannel.send(`out`)
                sentCounter.increment()
              }
            }
            ?.onFailure { logger.error(it) { "${this@SendingReceiver} failed to send messages" } }
      }

  override fun toString() = name

  /**
   * [SendingReceiver.Builder] builds a [MessagingComponent] instance that receives messages and can
   * send the result of processing each message to **receiver** *messaging component(s)*.
   *
   * @param In the type that is received
   * @param Out the type that is sent
   * @property receiveFrom the queue to receive from
   * @property sendTo the queue to send to
   * @property generatingMessageHandler the function that is invoked when a message is received, it
   * accepts an instance of [In] and generates [Out] instance(s)
   * @property deserializer the deserializer to use to construct instances of type [In]
   * @property serializer the serializer to use to convert instances of [Out] to bytes
   */
  class Builder<In, Out> internal constructor() : MessagingComponent.Builder() {

    var receiveFrom: String by Delegates.notNull()
    var sendTo: String by Delegates.notNull()

    private var generatingMessageHandler: GeneratingMessageHandler<In, Out> by Delegates.notNull()
    private var deserializer: Deserializer<In> by Delegates.notNull()
    private var serializer: Serializer<Out> by Delegates.notNull()

    /**
     * Use the [generatingMessageHandler] for this [SendingReceiver.Builder].
     *
     * @param generatingMessageHandler the [generatingMessageHandler] to use to process received
     * messages
     */
    fun onMessage(generatingMessageHandler: GeneratingMessageHandler<In, Out>) {
      this.generatingMessageHandler = generatingMessageHandler
    }

    /**
     * Use the [Function] of [In] to [Publisher] of [Out] as the [GeneratingMessageHandler] for this
     * [SendingReceiver.Builder].
     *
     * The [messageToPublisherFunction] isn't a *suspendable* function and uses [Function] and
     * [Publisher] so that a [GeneratingMessageHandler] can be initialized idiomatically in *Java*
     * code. *Kotlin* users should consider/prefer using the [GeneratingMessageHandler] with the
     * [onMessage] function.
     *
     * @param messageToPublisherFunction the [Function] of [In] to [Publisher] or [Out] to use as
     * [generatingMessageHandler]
     */
    @Suppress("unused", "MemberVisibilityCanBePrivate")
    fun onMessage(messageToPublisherFunction: Function<in In, Publisher<out Out>>) {
      this.generatingMessageHandler =
          GeneratingMessageHandler { message -> messageToPublisherFunction.apply(message).asFlow() }
    }

    /**
     * Use the [deserializer] for this [SendingReceiver.Builder].
     *
     * @param deserializer the [Deserializer] to use to construct instances of type [In]
     */
    fun deserialize(deserializer: Deserializer<In>) {
      this.deserializer = deserializer
    }

    /**
     * Use the [serializer] for this [SendingReceiver.Builder].
     *
     * @param serializer the [Serializer] to use to convert instances of [Out] to bytes
     */
    fun serialize(serializer: Serializer<Out>) {
      this.serializer = serializer
    }

    /**
     * Build the [SendingReceiver].
     *
     * @param transport the [Transport] to use to send and receive messages
     * @return the [MessagingComponent]
     */
    override fun build(transport: Transport): MessagingComponent {
      return SendingReceiver(
          name,
          close,
          receiveFrom,
          sendTo,
          generatingMessageHandler,
          deserializer,
          serializer,
          transport)
    }
  }

  private companion object {

    val logger = KotlinLogging.logger {}
  }
}
