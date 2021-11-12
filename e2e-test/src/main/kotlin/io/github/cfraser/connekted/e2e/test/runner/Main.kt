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
package io.github.cfraser.connekted.e2e.test.runner

/**
 * The entry point for the example runner.
 *
 * Runs the example [io.github.cfraser.connekted.MessagingApplication] or
 * [io.github.cfraser.connekted.MessagingComponent] corresponding to the `run.example` property
 * value.
 */
fun main() {
  when (val messagingApplicationExample = System.getProperty("run.example")) {
    "example-02" -> io.github.cfraser.connekted.e2e.test.example02.main()
    "example-03" -> io.github.cfraser.connekted.e2e.test.example03.main()
    "example-04" -> io.github.cfraser.connekted.e2e.test.example04.main()
    "example-05" -> io.github.cfraser.connekted.e2e.test.example05.main()
    "example-06" -> io.github.cfraser.connekted.e2e.test.Example06.main()
    else -> error("unexpected `run.example` system property: $messagingApplicationExample")
  }
}
