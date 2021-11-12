@file:Suppress("PackageDirectoryMismatch")
// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.github.cfraser.connekted.e2e.test.example02

import io.github.cfraser.connekted.FixedIntervalSchedule
import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport
import kotlinx.coroutines.flow.flowOf
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

fun main() {
  Connekted(transport) {

addSender {
  name = "example-sender"
  sendTo = "incrementing-integers"
  schedule = FixedIntervalSchedule(Duration.ofSeconds(3))
  with(AtomicInteger()) {
    send { flowOf(getAndIncrement().also { logger.info { "sending $it" } }) }
  }
  serialize { i -> ByteBuffer.allocate(4).apply { putInt(i) }.run { array() } }
}

  }
    .run()
}
