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
package io.github.cfraser.connekted.operator.api.v0

import io.github.cfraser.connekted.operator.registry.MessagingComponentRegistry
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * [MessagingComponentController] is a reactive [RestController] for getting data about *messaging
 * components*.
 *
 * @property messagingComponentRegistry the [MessagingComponentRegistry] to get *messaging
 * component* data from
 */
@Api
@RestController
@RequestMapping("\${connekted.v0.api.path}/messaging-component")
internal class MessagingComponentController(
    private val messagingComponentRegistry: MessagingComponentRegistry
) {

  @ApiOperation(
      value = "Get the messaging application names for a messaging component.",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @GetMapping("/{name}/messaging-applications")
  fun getMessagingApplicationNames(
      @PathVariable
      @ApiParam("The name of the messaging component to get messaging application names for.")
      name: String
  ): Mono<Set<String>> {
    return mono(Dispatchers.Default) { messagingComponentRegistry.messagingApplicationNames(name) }
  }
}
