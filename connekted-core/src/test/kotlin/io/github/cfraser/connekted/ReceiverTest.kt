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

import io.github.cfraser.connekt.local.LocalTransport
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.fail

class ReceiverTest {

  private val closed = atomic(false)
  private val received = atomic(0)

  @Test
  @Timeout(10, unit = TimeUnit.SECONDS)
  fun testReceiver() {
    val onClose: () -> Unit = {
      if (!closed.compareAndSet(expect = false, update = true)) fail("receiver already closed")
    }

    val messages: MutableCollection<Int> = ConcurrentLinkedQueue()
    val messageHandler = MessageHandler<Int> { message -> messages += message }
    val numberOfMessages = 10
    val queue = "test-queue"
    val transport = LocalTransport()
    val receiver =
        Receiver("test-receiver", onClose, queue, messageHandler, intDeserializer, transport)

    runBlocking {
      receiver.apply { start() }

      launch {
        val sendChannel = transport.sendTo(queue, intSerializer)
        repeat(numberOfMessages) {
          delay(500)
          sendChannel.send(received.incrementAndGet())
        }
      }

      // Shutdown the receiver after receiving desired number of messages
      while (received.value < numberOfMessages) delay(100)

      receiver.shutdown()
    }

    assertTrue { (1..numberOfMessages).all { messages.contains(it) } }
  }
}
