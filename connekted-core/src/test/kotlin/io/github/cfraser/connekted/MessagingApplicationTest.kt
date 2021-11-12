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
@file:OptIn(DelicateCoroutinesApi::class, ExperimentalTime::class)

package io.github.cfraser.connekted

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.github.cfraser.connekt.local.LocalTransport
import io.github.cfraser.connekted.ktor.KtorMessagingApplication
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import java.time.Duration
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.ExperimentalTime
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.commons.lang3.RandomStringUtils
import org.junit.jupiter.api.Timeout

internal class MessagingApplicationTest {

  private val receiverClosed = atomic(false)
  private val receiverMsgAppClosed = atomic(false)
  private val sendingReceiverClosed = atomic(false)
  private val sendingReceiverMsgAppClosed = atomic(false)
  private val senderClosed = atomic(false)
  private val senderMsgAppClosed = atomic(false)

  @BeforeTest
  fun beforeTest() {
    receiverClosed.lazySet(false)
    receiverMsgAppClosed.lazySet(false)
    sendingReceiverClosed.lazySet(false)
    sendingReceiverMsgAppClosed.lazySet(false)
    senderClosed.lazySet(false)
    senderMsgAppClosed.lazySet(false)

    mockkObject(Envs)
    every { Envs.name } returns messagingApplicationName
    every { Envs.username } returns username
    every { Envs.password } returns password
    every { Envs.messagingToken } returns token
  }

  @AfterTest
  fun afterTest() {
    unmockkAll()
  }

  @Test
  @Timeout(1, unit = TimeUnit.MINUTES)
  fun `Run messaging applications and verify functionality`() {
    val receivedByReceiver = Collections.synchronizedList(mutableListOf<Message>())
    val receiverMsgApp = messagingApplication {
      addReceiver {
        name = receiverName
        receiveFrom = receiverName
        onMessage { message -> receivedByReceiver.add(message) }
        deserialize { Message.deserialize(it) }
        onClose {
          if (!receiverClosed.compareAndSet(expect = false, update = true))
              fail("receiver already closed")
        }
      }
      onClose {
        if (!receiverMsgAppClosed.compareAndSet(expect = false, update = true))
            fail("receiver messaging application already closed")
      }
    }

    val transformMessage = { message: Message -> message.copy(updated = true) }
    val sendingReceiverMsgApp = messagingApplication {
      addSendingReceiver {
        name = sendingReceiverName
        receiveFrom = sendingReceiverName
        sendTo = receiverName
        onMessage { message -> flowOf(transformMessage(message)) }
        deserialize { Message.deserialize(it) }
        serialize { Message.serialize(it) }
        onClose {
          if (!sendingReceiverClosed.compareAndSet(expect = false, update = true))
              fail("sending-receiver already closed")
        }
      }
      onClose {
        if (!sendingReceiverMsgAppClosed.compareAndSet(expect = false, update = true))
            fail("sending-receiver messaging application already closed")
      }
    }

    val sentBySender = Collections.synchronizedList(mutableListOf<Message>())
    val senderMsgApp = messagingApplication {
      addSender {
        name = senderName
        sendTo = sendingReceiverName
        schedule = FixedIntervalSchedule(Duration.ofSeconds(1))
        send {
          if (sentBySender.size.toLong() == sendMessages) delay(INFINITE)
          flowOf(
              Message(
                  RandomStringUtils.randomAlphanumeric(10), Random.nextInt(), Random.nextLong()))
              .apply { collect { sentBySender.add(it) } }
        }
        serialize { Message.serialize(it) }
        onClose {
          if (!senderClosed.compareAndSet(expect = false, update = true))
              fail("sender already closed")
        }
      }
      onClose {
        if (!senderMsgAppClosed.compareAndSet(expect = false, update = true))
            fail("sender messaging application already closed")
      }
    }

    val messagingApplications = listOf(receiverMsgApp, sendingReceiverMsgApp, senderMsgApp)

    for (messagingApplication in messagingApplications) GlobalScope.launch {
      messagingApplication.start()
    }

    while (receivedByReceiver.size < sendMessages) Thread.sleep(100)

    assertEquals(sentBySender.size, receivedByReceiver.size)
    assertTrue(sentBySender.map { transformMessage(it) }.containsAll(receivedByReceiver))

    runBlocking {
      receiverMsgApp.checkMessagingApplicationData(receiverName)
      sendingReceiverMsgApp.checkMessagingApplicationData(sendingReceiverName)
      senderMsgApp.checkMessagingApplicationData(senderName)

      for (messagingApplication in messagingApplications) messagingApplication.shutdown()
    }

    assertTrue { receiverClosed.value }
    assertTrue { receiverMsgAppClosed.value }
    assertTrue { sendingReceiverClosed.value }
    assertTrue { sendingReceiverMsgAppClosed.value }
    assertTrue { senderClosed.value }
    assertTrue { senderMsgAppClosed.value }
  }

  private companion object {

    val transport = LocalTransport()

    const val messagingApplicationName = "test"
    const val receiverName = "test-receiver"
    const val sendingReceiverName = "test-sending-receiver"
    const val senderName = "test-sender"
    const val username = "test-username"
    const val password = "test-password"
    const val token = "test-token"
    const val sendMessages = 10L

    /**
     * Initialize a *messaging application*.
     *
     * Construct a [MessagingApplication.Builder] and apply the [initializer].
     *
     * @param initializer the [MessagingApplication] initialization block
     * @see MessagingApplication.Builder for more information about building a
     * [MessagingApplication]
     */
    private fun messagingApplication(
        initializer: MessagingApplication.Builder.() -> Unit
    ): MessagingApplication {
      return MessagingApplication.Builder(transport)
          .apply(initializer)
          .apply {
            applicationEngineInitializer =
                KtorMessagingApplication.ApplicationEngineInitializer {
                  TestApplicationEngine(createTestEnvironment()).apply { start() }
                }
          }
          .build()
    }

    private suspend fun MessagingApplication.checkMessagingApplicationData(
        messagingComponentName: String
    ) {
      val msgAppData = (this as? KtorMessagingApplication)?.data()
      assertNotNull(msgAppData)
      assertEquals(messagingApplicationName, msgAppData.name)
      assertEquals(1, msgAppData.messagingComponents!!.size)
      with(msgAppData.messagingComponents!!.first()) {
        when (messagingComponentName) {
          sendingReceiverName -> {
            assertEquals(sendingReceiverName, name)
            assertEquals(sendMessages, messagingData!!.received)
            assertEquals(0, messagingData!!.receiveErrors)
            assertEquals(receiverName, messagingData!!.sendTo)
            assertEquals(sendMessages, messagingData!!.sent)
            assertEquals(0, messagingData!!.sendErrors)
          }
          senderName -> {
            assertEquals(senderName, name)
            assertEquals(sendingReceiverName, messagingData!!.sendTo)
            assertEquals(sendMessages, messagingData!!.sent)
            assertEquals(0, messagingData!!.sendErrors)
          }
          receiverName -> {
            assertEquals(receiverName, name)
            assertEquals(sendMessages, messagingData!!.received)
            assertEquals(0, messagingData!!.receiveErrors)
          }
          else -> fail("unexpected messaging component name")
        }
      }
    }
  }

  private data class Message(val a: String, val b: Int, val c: Long, val updated: Boolean = false) {

    companion object {

      private val objectMapper: ObjectMapper = jacksonObjectMapper()

      fun serialize(message: Message): ByteArray = objectMapper.writeValueAsBytes(message)

      fun deserialize(byteArray: ByteArray): Message = objectMapper.readValue(byteArray)
    }
  }
}
