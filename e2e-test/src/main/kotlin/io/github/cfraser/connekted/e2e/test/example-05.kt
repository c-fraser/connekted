@file:Suppress("PackageDirectoryMismatch")
// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.github.cfraser.connekted.e2e.test.example05

import io.github.cfraser.connekted.FixedIntervalSchedule
import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport
import kotlinx.coroutines.flow.flow
import org.apache.commons.lang3.RandomStringUtils
import java.time.Duration

fun main() {
  Connekted(transport) {
    addSender {
      name = "random-strings-sender"
      sendTo = "random-strings"
      schedule = FixedIntervalSchedule(Duration.ofSeconds(30))
      send {
        flow<String> {
          repeat(5) {
            val randomString = RandomStringUtils.randomAlphabetic(5)
            logger.info { "sending $randomString" }
            emit(randomString)
          }
        }
      }
      serialize(String::toByteArray)
    }

    addReceiver {
      name = "reversed-random-strings-receiver"
      receiveFrom = "reversed-random-strings"
      onMessage { message -> logger.info { "received $message" } }
      deserialize { bytes -> String(bytes) }
    }
  }
    .run()
}
