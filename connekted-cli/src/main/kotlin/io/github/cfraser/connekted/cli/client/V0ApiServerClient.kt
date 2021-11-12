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
package io.github.cfraser.connekted.cli.client

import io.github.cfraser.connekted.common.MessagingApplicationData
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam

/**
 * The [V0ApiServerClient] interface defines endpoints accessible via the v0.x *messaging
 * application operator* API.
 */
@Path("/api/v0")
internal interface V0ApiServerClient {

  /**
   * Get [MessagingApplicationData] for a *messaging application* by [name].
   *
   * @param name the name of the messaging application to get
   * @return the [MessagingApplicationData] or `null` if the messaging application doesn't exist
   */
  @GET
  @Path("/messaging-application/{name}")
  fun getMessagingApplication(@PathParam("name") name: String): MessagingApplicationData?

  /**
   * Get the [MessagingApplicationData] for all *messaging applications*.
   *
   * @return the [Set] of [MessagingApplicationData]
   */
  @GET @Path("/messaging-application") fun getMessagingApplications(): Set<MessagingApplicationData>
}
