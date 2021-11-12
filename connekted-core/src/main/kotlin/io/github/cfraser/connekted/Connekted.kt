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

import io.github.cfraser.connekt.api.Transport
import io.github.cfraser.connekted.Connekted.invoke
import java.io.Closeable
import java.util.function.Consumer
import kotlinx.atomicfu.atomic

/** The [Connekted] object builds and manages the execution of a [MessagingApplication]. */
object Connekted : Runnable, Closeable {

  private val messagingApplication = atomic<MessagingApplication?>(null)

  /**
   * Initialize a *messaging application*.
   *
   * @param transport the [Transport] to use to send and receive messages
   * @param initializer the [MessagingApplication.Builder] initialization block
   */
  operator fun invoke(transport: Transport, initializer: MessagingApplication.Builder.() -> Unit) =
      apply {
    if (!messagingApplication.compareAndSet(
        expect = null, update = MessagingApplication.Builder(transport).apply(initializer).build()))
        error("The messaging application is already initialized")
  }

  /**
   * Initialize a *messaging application*.
   *
   * This function is [JvmStatic] and uses [Consumer] so that [Connekted] can be initialized
   * idiomatically in *Java* code. *Kotlin* users should consider/prefer using the [invoke]
   * function.
   *
   * @param transport the [Transport] to use to send and receive messages
   * @param initializer the [Consumer] of [MessagingApplication.Builder]
   * @see invoke
   */
  @JvmStatic
  fun initialize(
      transport: Transport,
      initializer: Consumer<MessagingApplication.Builder>
  ): Connekted {
    return invoke(transport, initializer::accept)
  }

  /**
   * Run the [messagingApplication].
   *
   * Running the [messagingApplication] is a **blocking** operation, this function will not return
   * until the [messagingApplication] is [MessagingApplication.shutdown].
   */
  override fun run() {
    messagingApplication.value?.start() ?: error("the messaging application is not initialized")
  }

  /** Shutdown the [messagingApplication]. */
  override fun close() {
    messagingApplication.value?.shutdown()
  }

  /** [Connekted.Dsl] is an annotation class that applies the [DslMarker] to the target class. */
  @DslMarker
  @Target(AnnotationTarget.CLASS)
  @Retention(AnnotationRetention.BINARY)
  internal annotation class Dsl
}
