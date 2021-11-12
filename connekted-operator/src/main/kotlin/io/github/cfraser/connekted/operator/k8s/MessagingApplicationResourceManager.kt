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
package io.github.cfraser.connekted.operator.k8s

import io.github.cfraser.connekted.k8s.MessagingApplication
import io.github.cfraser.connekted.k8s.control.MessagingApplicationControl
import io.github.cfraser.connekted.operator.registry.MessagingApplicationRegistry
import io.javaoperatorsdk.operator.api.Context
import io.javaoperatorsdk.operator.api.Controller as OperatorSdkController
import io.javaoperatorsdk.operator.api.DeleteControl
import io.javaoperatorsdk.operator.api.ResourceController
import io.javaoperatorsdk.operator.api.UpdateControl
import org.springframework.stereotype.Component

/**
 * [MessagingApplicationResourceManager] is the [ResourceController] implementation for the
 * [MessagingApplication] custom resource.
 *
 * @property messagingApplicationControl the [MessagingApplicationControl] handles creation and
 * deletion of [MessagingApplication] resources
 */
@Component
@OperatorSdkController(
    name = "messaging-application-controller",
    namespaces = [OperatorSdkController.WATCH_CURRENT_NAMESPACE])
internal class MessagingApplicationResourceManager(
    private val messagingApplicationControl: MessagingApplicationControl,
    private val messagingApplicationRegistry: MessagingApplicationRegistry
) : ResourceController<MessagingApplication> {

  override fun deleteResource(
      resource: MessagingApplication,
      context: Context<MessagingApplication>
  ): DeleteControl {
    // Deregister the messaging application from the registry
    messagingApplicationRegistry.deregister(resource.metadata.name)
    messagingApplicationControl.delete(resource)
    return DeleteControl.DEFAULT_DELETE
  }

  override fun createOrUpdateResource(
      resource: MessagingApplication,
      context: Context<MessagingApplication>
  ): UpdateControl<MessagingApplication> {
    messagingApplicationControl.createOrReplace(resource)
    // Register the messaging application in the registry
    messagingApplicationRegistry.register(resource.metadata.name)
    return UpdateControl.updateCustomResource(resource)
  }
}
