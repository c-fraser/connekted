@file:Suppress("PackageDirectoryMismatch")
// This file was automatically generated from README.md by Knit tool. Do not edit.
package io.github.cfraser.connekted.e2e.test.example01

import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport

fun main() {

Connekted(transport) {
  addSender<Nothing> { TODO() }
  addReceiver<Nothing> { TODO() }
  addSendingReceiver<Nothing, Nothing> { TODO() }
}
  .run()
}
