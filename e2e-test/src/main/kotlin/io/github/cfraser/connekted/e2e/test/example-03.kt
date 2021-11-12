@file:Suppress("PackageDirectoryMismatch")
// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.github.cfraser.connekted.e2e.test.example03

import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport
import java.nio.ByteBuffer

fun main() {
  Connekted(transport) {

addReceiver {
  name = "example-receiver"
  receiveFrom = "incrementing-integers"
  onMessage { i -> logger.info { "received $i" } }
  deserialize { bytes -> ByteBuffer.wrap(bytes).int }
}
  }
    .run()
}
