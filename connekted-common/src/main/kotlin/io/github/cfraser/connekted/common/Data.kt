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

/**
 * [MessagingApplicationData] contains data describing a *messaging application*.
 *
 * [sent], [received], [sendErrors] and [receiveErrors] are somewhat implementation specific, if
 * message transport becomes interchangeable in the future, these will have to be reevaluated.
 *
 * @property name the name of the messaging application
 * @property messagingComponents the messaging components within the messaging application
 * @property sent the number of request frames sent by the messaging application
 * @property sendErrors the number of send errors for the messaging application
 * @property received the number of request frames received by the messaging application
 * @property receiveErrors the number of receive errors for the messaging application
 */
data class MessagingApplicationData(
    val name: String? = null,
    val messagingComponents: List<MessagingComponentData>? = null,
    val sent: Long? = null,
    val sendErrors: Long? = null,
    val received: Long? = null,
    val receiveErrors: Long? = null
)

/**
 * [MessagingComponentData] contains data describing a *messaging component*.
 *
 * @property name the name of the messaging component
 * @property type the type of the messaging component
 * @property messagingData the [MessagingData] for the messaging component
 */
data class MessagingComponentData(
    val name: String? = null,
    val type: Type? = null,
    val messagingData: MessagingData? = null,
) {

  enum class Type {
    SENDER,
    RECEIVER,
    SENDING_RECEIVER
  }
}

/**
 * [MessagingData] encapsulates the messaging data/metrics for a *messaging component*.
 *
 * @property sendTo the name of the *messaging component* to send to
 * @property sent the number of messages sent
 * @property sendErrors the number of messages unable to be sent
 * @property receiveFrom the queue to receive from
 * @property received the number of messages received
 * @property receiveErrors the number of messages unable to be received
 */
data class MessagingData(
    val sendTo: String? = null,
    val sent: Long? = null,
    val sendErrors: Long? = null,
    val receiveFrom: String? = null,
    val received: Long? = null,
    val receiveErrors: Long? = null
)
