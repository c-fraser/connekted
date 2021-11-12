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
package io.github.cfraser.connekted.k8s

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import io.fabric8.kubernetes.api.model.KubernetesResource
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition
import io.fabric8.kubernetes.api.model.apiextensions.v1.JSONSchemaPropsBuilder
import io.fabric8.kubernetes.client.CustomResource
import io.fabric8.kubernetes.client.CustomResourceList
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Version

/**
 * [MessagingApplication] is the [CustomResource] type managed by the projects'
 * [operator](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/) implementation.
 *
 * [MessagingApplication] is annotated with [Group] and [Version] which determine how to identify
 * the custom resource via the Kubernetes API.
 */
@Group("connekted.cfraser.github.io")
@Version("v0")
class MessagingApplication : CustomResource<MessagingApplication.Spec, Void>() {

  companion object {

    /** The key for the name included in the label metadata for [MessagingApplication] resources. */
    const val nameKey = "messaging-application.name"

    /** The [CustomResourceDefinition] for the [MessagingApplication] custom resource. */
    val customResourceDefinition: CustomResourceDefinition =
        CustomResourceDefinitionContext.v1CRDFromCustomResourceType(
                MessagingApplication::class.java)
            .editSpec()
            .editFirstVersion()
            .withNewSchema()
            .withNewOpenAPIV3Schema()
            .withType("object")
            .addToRequired("spec")
            .addToProperties(
                "spec",
                JSONSchemaPropsBuilder()
                    .withType("object")
                    .addToProperties(
                        "image",
                        JSONSchemaPropsBuilder()
                            .withDescription("The image reference for the messaging application")
                            .withType("string")
                            .build())
                    .addToProperties(
                        "config",
                        JSONSchemaPropsBuilder()
                            .withDescription("The messaging application configuration")
                            .withType("string")
                            .withNullable(true)
                            .build())
                    .build())
            .endOpenAPIV3Schema()
            .endSchema()
            .endVersion()
            .endSpec()
            .build()
  }

  /**
   * The specification for the [MessagingApplication] custom resource.
   *
   * @property image the messaging application image reference
   * @property config the messaging application config
   */
  @JsonDeserialize
  data class Spec(
      var image: String? = null,
      var config: String? = null,
  ) : KubernetesResource

  /** The [CustomResourceList] type for [MessagingApplication]. */
  class List : CustomResourceList<MessagingApplication>()
}
