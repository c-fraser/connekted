@file:Suppress("PackageDirectoryMismatch")
// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.github.cfraser.connekted.e2e.test.example04

import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport
import kotlinx.coroutines.flow.flowOf
import org.apache.commons.lang3.StringUtils

fun main() {
  Connekted(transport) {

addSendingReceiver {
  name = "example-sending-receiver"
  receiveFrom = "random-strings"
  sendTo = "reversed-random-strings"
  onMessage { randomString -> flowOf(StringUtils.reverse(randomString)) }
  deserialize { bytes -> String(bytes) }
  serialize(String::toByteArray)
}
  }
    .run()
}
