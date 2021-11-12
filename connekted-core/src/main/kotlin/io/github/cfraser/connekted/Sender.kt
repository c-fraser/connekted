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

import io.github.cfraser.connekt.api.Serializer
import io.github.cfraser.connekt.api.Transport
import io.github.cfraser.connekted.common.MessagingData
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import java.time.Duration
import java.time.Instant
import java.util.function.Supplier
import kotlin.properties.Delegates
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.reactivestreams.Publisher

/**
 * The [Sender] class defines a [MessagingComponent] that sends messages, via the [sendChannel],
 * periodically according to a [schedule].
 *
 * Each scheduled function is invoked within a coroutine, when a scheduled function is not executing
 * (in between invocations) it does not block (suspends via [delay]).
 *
 * @param T the type that is sent
 * @property name the name of the messaging component
 * @property onClose the function to invoke when the [Sender] is closed
 * @property sendTo the queue to send to
 * @property schedule the schedule which determines the execution times
 * @property messageGenerator the [MessageGenerator] which generates messages to send
 * @param serializer the [Serializer] which converts instances of [T] to bytes
 * @param transport the [Transport] to use to send messages
 */
class Sender<T>
internal constructor(
    override val name: String,
    private val onClose: () -> Unit,
    private val sendTo: String,
    private val schedule: Schedule,
    private val messageGenerator: MessageGenerator<T>,
    serializer: Serializer<T>,
    transport: Transport
) : MessagingComponent {

  private val sendChannel by lazy { transport.sendTo(sendTo, serializer) }
  private val previousExecutionTime = atomic<Instant?>(null)
  private val scheduledJob = atomic<Job?>(null)

  private val tag = Tag.of(Sender::class.simpleName!!.lowercase(), name)
  private val generatedCounter = Metrics.meterRegistry.counter("generated-messages", Tags.of(tag))
  private val sentCounter = Metrics.meterRegistry.counter("sent-messages", Tags.of(tag))

  /** Start the [Sender]. */
  override fun CoroutineScope.start() {
    scheduledJob.compareAndSet(
        expect = null,
        update =
            launch {
              do {
                delay(nextExecution())
                try {
                  previousExecutionTime.update { Instant.now() }
                  logger.debug { "executing scheduled function for ${this@Sender}" }
                  runCatching { messageGenerator.generate() }
                      .onFailure {
                        logger.error(it) { "${this@Sender} failed to generate messages" }
                      }
                      .getOrNull()
                      ?.onEach { generatedCounter.increment() }
                      ?.runCatching {
                        collect { message ->
                          sendChannel.send(message)
                          sentCounter.increment()
                        }
                      }
                      ?.onFailure { logger.error(it) { "${this@Sender} failed to send messages" } }
                } catch (e: CancellationException) {
                  logger.warn(e) { "${this@Sender} cancelled" }
                }
              } while (isActive)
            })
  }

  /** Shutdown the [Sender]. */
  override suspend fun shutdown() {
    try {
      scheduledJob.value?.cancelAndJoin()
    } catch (_: CancellationException) {}
    runCatching(onClose).onFailure { logger.error(it) { "exception occurred during close" } }
  }

  override suspend fun data() =
      withContext(Dispatchers.Default) {
        MessagingData(
            sendTo = sendTo,
            sent = sentCounter.count().toLong(),
            sendErrors = (generatedCounter.count() - sentCounter.count()).toLong())
      }

  /**
   * Return the milliseconds until the next execution calculated from the [previousExecutionTime]
   * and [schedule].
   *
   * @return the milliseconds until the next execution
   */
  private suspend fun nextExecution(): Long {
    return withContext(Dispatchers.Default) {
      val now = Instant.now()
      val nextExecutionTime = schedule.nextExecutionTime(now, previousExecutionTime.value)
      val duration =
          if (nextExecutionTime.isAfter(now)) Duration.between(now, nextExecutionTime)
          else Duration.ZERO
      duration.toMillis()
    }
  }

  override fun toString() = name

  /**
   * [Sender.Builder] builds a [MessagingComponent] instance that periodically sends messages to a
   * queue.
   *
   * @param T the type to send
   * @property sendTo the queue to send to
   * @property schedule the schedule which determines the execution times
   * @property messageGenerator the function that is invoked to generate the messages to send
   * @property serializer the serializer to use to convert instances of [T] to bytes
   */
  class Builder<T> internal constructor() : MessagingComponent.Builder() {

    var sendTo: String by Delegates.notNull()
    var schedule: Schedule by Delegates.notNull()

    private var messageGenerator: MessageGenerator<T> by Delegates.notNull()
    private var serializer: Serializer<T> by Delegates.notNull()

    /**
     * Use the [messageGenerator] for this [Sender.Builder].
     *
     * @param messageGenerator the [MessageGenerator] to generate messages to send
     */
    fun send(messageGenerator: MessageGenerator<T>) {
      this.messageGenerator = messageGenerator
    }

    /**
     * Use the [Supplier] of [Publisher] as the [MessageGenerator] for this [Sender.Builder].
     *
     * The [publisherSupplier] isn't a *suspendable* function and uses [Supplier] and [Publisher] so
     * that a [MessageGenerator] can be initialized idiomatically in *Java* code. *Kotlin* users
     * should consider/prefer using the [MessageGenerator] with the [send] function.
     *
     * @param publisherSupplier the [Supplier] of [Publisher] to use as [messageGenerator]
     */
    fun send(publisherSupplier: Supplier<Publisher<out T>>) {
      this.messageGenerator = MessageGenerator { publisherSupplier.get().asFlow() }
    }

    /**
     * Use the [serializer] for this [Sender.Builder].
     *
     * @param serializer the [Serializer] to use to convert instances of [T] to bytes
     */
    fun serialize(serializer: Serializer<T>) {
      this.serializer = serializer
    }

    /**
     * Build the [Sender].
     *
     * @param transport the [Transport] to use to send messages
     * @return the [MessagingComponent]
     */
    override fun build(transport: Transport): MessagingComponent {
      return Sender(name, close, sendTo, schedule, messageGenerator, serializer, transport)
    }
  }

  private companion object {

    val logger = KotlinLogging.logger {}
  }
}
