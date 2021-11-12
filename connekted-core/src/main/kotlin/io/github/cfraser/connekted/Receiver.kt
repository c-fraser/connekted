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
import io.github.cfraser.connekt.api.Transport
import io.github.cfraser.connekted.common.MessagingData
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import java.util.function.Consumer
import kotlin.properties.Delegates
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import mu.KotlinLogging

/**
 * The [Receiver] class defines a [MessagingComponent] type that receives messages, via the
 * [receiveChannel], and handles/processes them.
 *
 * @param T the type that is received and handled
 * @property name the name of the messaging component
 * @property onClose the function to invoke when the [Receiver] is closed
 * @property receiveFrom the queue to receive messages from
 * @property messageHandler the [MessageHandler] to use to handle received instances of type [T]
 * @property deserializer the [Deserializer] to use to construct instances of type [T]
 * @param transport the [Transport] to use to receive messages
 */
class Receiver<T>
internal constructor(
    override val name: String,
    private val onClose: () -> Unit,
    private val receiveFrom: String,
    private val messageHandler: MessageHandler<T>,
    private val deserializer: Deserializer<T>,
    transport: Transport
) : MessagingComponent {

  private val receiveChannel by lazy { transport.receiveFrom(receiveFrom, deserializer) }
  private val running = atomic(false)
  private val processing = atomic<Job?>(null)

  private val tag = Tag.of(Receiver::class.simpleName!!.lowercase(), name)
  internal val receivedCounter = Metrics.meterRegistry.counter("received-messages", Tags.of(tag))
  internal val handledCounter = Metrics.meterRegistry.counter("handled-messages", Tags.of(tag))

  /**
   * Start the [Receiver].
   *
   * @throws IllegalStateException if the [Receiver] is in an invalid state
   */
  @Throws(IllegalStateException::class)
  override fun CoroutineScope.start() {
    if (!running.compareAndSet(expect = false, update = true)) {
      logger.warn { "Attempted to start ${this@Receiver} which is currently running" }
      return
    }

    logger.debug { "Starting ${this@Receiver}" }

    // Create a coroutine to receive messages and process them, also store a reference to the job so
    // that it can be cancelled during shutdown
    processing.compareAndSet(
        expect = null,
        update =
            receiveChannel
                .consumeAsFlow()
                .onEach { message ->
                  receivedCounter.increment()
                  runCatching { messageHandler.handle(message) }
                      .onFailure {
                        logger.error(it) { "${this@Receiver} failed to handle message" }
                      }
                      .onSuccess { handledCounter.increment() }
                }
                .launchIn(this@start))

    logger.debug { "Launched processing coroutine for ${this@Receiver}" }
  }

  /**
   * Shutdown the [Receiver].
   *
   * @throws [IllegalStateException] if the [Receiver] is in an invalid states
   */
  @Throws(IllegalStateException::class)
  override suspend fun shutdown() {
    if (!running.compareAndSet(expect = true, update = false)) {
      logger.warn { "Attempted to shutdown $this but it is not currently running" }
      return
    }

    logger.debug { "Closing $this" }

    try {
      processing.getAndSet(null)?.run {
        ensureActive()
        cancel()
      }
    } catch (t: Throwable) {
      logger.warn(t) { "Exception occurred while closing message receiver" }
    }

    runCatching(onClose).onFailure { logger.error(it) { "Exception occurred during close" } }

    logger.debug { "$this closed" }
  }

  override suspend fun data() =
      withContext(Dispatchers.Default) {
        MessagingData(
            receiveFrom = receiveFrom,
            received = receivedCounter.count().toLong(),
            receiveErrors = (receivedCounter.count() - handledCounter.count()).toLong())
      }

  override fun toString() = name

  /**
   * [Receiver.Builder] builds a [MessagingComponent] instance that handles messages received from a
   * queue.
   *
   * @param T the type to receive
   * @property receiveFrom the queue to receive from
   * @property messageHandler the function that is invoked when a message is received
   * @property deserializer the deserializer to use to construct instances of type [T]
   */
  class Builder<T> internal constructor() : MessagingComponent.Builder() {

    var receiveFrom: String by Delegates.notNull()

    private var messageHandler: MessageHandler<T> by Delegates.notNull()
    private var deserializer: Deserializer<T> by Delegates.notNull()

    /**
     * Use the [messageHandler] for this [Receiver.Builder].
     *
     * @param messageHandler the [MessageHandler] to use to process received messages
     */
    fun onMessage(messageHandler: MessageHandler<T>) {
      this.messageHandler = messageHandler
    }

    /**
     * Use the [Consumer] as the [MessageHandler] for this [Receiver.Builder].
     *
     * The [messageConsumer] isn't a *suspendable* function and uses [Consumer] so that a
     * [MessageHandler] can be initialized idiomatically in *Java* code. *Kotlin* users should
     * consider/prefer using the [MessageHandler] with the [onMessage] function.
     *
     * @param messageConsumer the [Consumer] to use as [messageHandler]
     */
    fun onMessage(messageConsumer: Consumer<T>) {
      // Using the instance function reference syntax (`MessageHandler(messageConsumer::accept)`)
      // causes a cryptic compiler error
      this.messageHandler = MessageHandler { message -> messageConsumer.accept(message) }
    }

    /**
     * Use the [deserializer] for this [Receiver.Builder].
     *
     * @param deserializer the [Deserializer] to use to construct instances of type [T]
     */
    fun deserialize(deserializer: Deserializer<T>) {
      this.deserializer = deserializer
    }

    /**
     * Build the [Receiver].
     *
     * @param transport the [Transport] to use to receive messages
     * @return the [MessagingComponent]
     */
    override fun build(transport: Transport): MessagingComponent {
      return Receiver(name, close, receiveFrom, messageHandler, deserializer, transport)
    }
  }

  private companion object {

    val logger = KotlinLogging.logger {}
  }
}
