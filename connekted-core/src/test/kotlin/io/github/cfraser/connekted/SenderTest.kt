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

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import io.github.cfraser.connekt.local.LocalTransport
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.fail

class SenderTest {

  private val closed = atomic(false)
  private val generated = atomic(0)

  @BeforeTest
  fun beforeTest() {
    closed.value = false
    generated.value = 0
  }

  @Test
  @Timeout(10, unit = TimeUnit.SECONDS)
  fun testFixedIntervalSchedule() {
    FixedIntervalSchedule(Duration.ofMillis(500)).testSend()
  }

  @Test
  @Timeout(15, unit = TimeUnit.SECONDS)
  fun testInitialDelaySchedule() {
    InitialDelaySchedule(Duration.ofSeconds(3), FixedIntervalSchedule(Duration.ofMillis(500)))
        .testSend()
  }

  /**
   * The minimum interval a CRON schedule can run at is every minute. Consequently, this test would
   * have to run for at least a minute to verify the [Sender]. For this reason it is currently
   * [Disabled], it is unreasonable for a single unit test to take such a long time.
   */
  @Disabled
  @Test
  @Timeout(5, unit = TimeUnit.MINUTES)
  fun testCronSchedule() {
    CronSchedule(
            CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX))
                .parse("* * * * *"))
        .testSend(numberOfMessages = 3)
  }

  /**
   * Run a [Sender] with the [Schedule] and verify [numberOfMessages] were sent.
   *
   * @param numberOfMessages the number of messages to send
   */
  private fun Schedule.testSend(numberOfMessages: Int = 10) {
    val onClose: () -> Unit = {
      if (!closed.compareAndSet(expect = false, update = true)) fail("sender already closed")
    }

    val messageGenerator = MessageGenerator { flowOf(generated.incrementAndGet()) }

    val sent: MutableCollection<Int> = ConcurrentLinkedQueue()
    val queue = "test-queue"
    val transport = LocalTransport()
    val sender =
        Sender("test-sender", onClose, queue, this, messageGenerator, intSerializer, transport)

    runBlocking {
      sender.apply { start() }

      launch {
        val receiveChannel = transport.receiveFrom(queue, intDeserializer)
        repeat(numberOfMessages) { sent += receiveChannel.receive() }
      }

      // Shutdown the sender after sending desired number of messages
      while (generated.value < numberOfMessages) delay(100)

      sender.shutdown()
    }

    assertTrue { (1..numberOfMessages).all { sent.contains(it) } }
  }
}
