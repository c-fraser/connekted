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
import io.github.cfraser.connekted.common.MessagingData
import kotlin.properties.Delegates
import kotlinx.coroutines.CoroutineScope
import mu.KLogger
import mu.KotlinLogging

/**
 * [MessagingComponent] is a generic type that represents a subprocess that sends and/or receives
 * messages. [MessagingComponent] implementations are created internally and returned through the
 * DSL functions and [io.github.cfraser.connekted.MessagingComponent.Builder] subtypes.
 */
interface MessagingComponent {

  /** The name of the [MessagingComponent]. */
  val name: String

  /** Start the [MessagingComponent] within the [CoroutineScope]. */
  fun CoroutineScope.start()

  /** Shutdown the [MessagingComponent]. */
  suspend fun shutdown()

  /**
   * Get the [MessagingData] for this [MessagingComponent].
   *
   * @return the [MessagingData]
   */
  suspend fun data(): MessagingData

  /**
   * The [MessagingComponent.Builder] abstract class defines common properties and functions for
   * [MessagingComponent] builders.
   *
   * This class is annotated with [Connekted.Dsl] because the subtypes will be used in the context
   * of a [type safe DSL](https://kotlinlang.org/docs/reference/type-safe-builders.html).
   */
  @Connekted.Dsl
  abstract class Builder internal constructor() {

    /**
     * The name of the [MessagingComponent] instance.
     *
     * The *messaging component* name is required to be unique within a *messaging application*.
     */
    var name: String by Delegates.notNull()

    /** The [KLogger] available for use by the messaging implementation. */
    val logger: KLogger by lazy {
      KotlinLogging.logger(runCatching { name }.getOrDefault(Envs.name))
    }

    protected var close: () -> Unit = {}

    /**
     * Use the [block] to [close] the [MessagingComponent] component.
     *
     * @param block the function to invoke when shutting down the messaging component
     */
    fun onClose(block: () -> Unit) {
      this.close = block
    }

    /**
     * Build the [MessagingComponent].
     *
     * @param transport the [Transport] to use to send and receive messages
     * @return the built messaging instance
     */
    internal abstract fun build(transport: Transport): MessagingComponent

    /**
     * Return a function which is the combination of the receiver "close" function and the
     * [AutoCloseable].
     *
     * @param closeable the [AutoCloseable] to close with the receiver
     * @return the combined close function
     */
    protected operator fun (() -> Unit).plus(closeable: AutoCloseable): () -> Unit {
      return {
        invoke()
        closeable.close()
      }
    }
  }
}
