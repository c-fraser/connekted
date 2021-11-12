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

import kotlinx.coroutines.flow.Flow

/**
 * A [MessageHandler] is a type that represents a callback which is invoked for each received
 * message.
 *
 * [MessageHandler] is a suspendable function which is executed when a message is received.
 *
 * @param T the type that is handled
 */
fun interface MessageHandler<in T> {

  /**
   * Handle the received [message].
   *
   * @param message the received message
   */
  suspend fun handle(message: T)
}

/**
 *
 * A [MessageGenerator] is a type that has the ability to generate an asynchronous stream of
 * messages.
 *
 * [MessageGenerator] is a suspendable function that generates a [Flow] of messages.
 *
 * @param T the type that is generated
 */
fun interface MessageGenerator<out T> {

  /**
   * Return the generated messages.
   *
   * @return the instances of type [T]
   */
  suspend fun generate(): Flow<T>
}

/**
 * A [GeneratingMessageHandler] is a type that represents a callback, which has the ability to
 * generate an asynchronous stream of messages, and is executed when a message is received.
 *
 * [GeneratingMessageHandler] is a suspendable function that generates a [Flow] of messages for each
 * received message.
 *
 * @param In the type that is handled
 * @param Out the type that is generated
 */
fun interface GeneratingMessageHandler<in In, out Out> {

  /**
   * Handle the received [message] and return resulting generated messages.
   *
   * @param message the received message
   * @return the generated messages
   */
  suspend fun handle(message: In): Flow<Out>
}
