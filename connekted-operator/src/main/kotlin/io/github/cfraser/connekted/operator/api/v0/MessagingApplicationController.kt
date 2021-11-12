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

import io.github.cfraser.connekted.common.MessagingApplicationData
import io.github.cfraser.connekted.operator.registry.MessagingApplicationRegistry
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import java.util.Optional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.mono
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * [MessagingApplicationController] is a reactive [RestController] for retrieving
 * [MessagingApplicationData] corresponding to running *messaging applications*.
 *
 * @property messagingApplicationRegistry the [MessagingApplicationRegistry] to get
 * [MessagingApplicationData] from
 */
@Api
@RestController
@RequestMapping("\${connekted.v0.api.path}/messaging-application")
internal class MessagingApplicationController(
    private val messagingApplicationRegistry: MessagingApplicationRegistry
) {

  @ApiOperation(
      value = "Get the messaging applications.", produces = MediaType.APPLICATION_JSON_VALUE)
  @GetMapping("")
  fun getAll(): Mono<Set<MessagingApplicationData>> {
    return mono(Dispatchers.Default) { messagingApplicationRegistry.getAll() }
  }

  @ApiOperation(value = "Get a messaging application.", produces = MediaType.APPLICATION_JSON_VALUE)
  @GetMapping("/{name}")
  fun get(
      @PathVariable @ApiParam("The name of the messaging application.") name: String
  ): Mono<ResponseEntity<MessagingApplicationData>> {
    return mono(Dispatchers.Default) { messagingApplicationRegistry[name] }.map { data ->
      ResponseEntity.of(Optional.ofNullable(data))
    }
  }
}
