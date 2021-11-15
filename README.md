# connekted

[![Build](https://github.com/c-fraser/connekted/workflows/build/badge.svg)](https://github.com/c-fraser/connekted/actions)
[![Release](https://img.shields.io/github/v/release/c-fraser/connekted?logo=github&sort=semver)](https://github.com/c-fraser/connekted/releases)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.c-fraser/connekted-core.svg)](https://search.maven.org/artifact/io.github.c-fraser/connekted-core)
[![Javadoc](https://javadoc.io/badge2/io.github.c-fraser/connekted-core/javadoc.svg)](https://javadoc.io/doc/io.github.c-fraser/connekted-core)
[![Apache License 2.0](https://img.shields.io/badge/License-Apache2-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Easily develop, deploy, and manage Kotlin (JVM) messaging applications
on [Kubernetes](https://kubernetes.io/).

## Contents

<!--- TOC -->

* [Motivation](#motivation)
* [Design](#design)
* [Usage](#usage)
    * [Library](#library)
        * [Messaging application](#messaging-application)
        * [Messaging components](#messaging-components)
            * [Sender](#sender)
            * [Receiver](#receiver)
            * [Sending-receiver](#sending-receiver)
    * [CLI](#cli)
        * [Prerequisites](#prerequisites)
        * [Deploy command](#deploy-command)
        * [Run command](#run-command)
        * [Get command](#get-command)
        * [Watch command](#watch-command)
        * [Stop command](#stop-command)
        * [Teardown command](#teardown-command)
* [Quickstart](#quickstart)
* [License](#license)

<!--- END -->

## Motivation

Designing and implementing performant and scalable message-oriented microservices from scratch is a
challenging endeavor. The `connekted` project provides a simplified (opinionated) approach making
the aforementioned able to be achieved with minimal effort. This is achieved through the utilization
of [Kubernetes](https://kubernetes.io/) combined with convenient libraries and tooling which handle
the vast majority of difficulties associated with creating and orchestrating distributed
applications.

## Design

The [core library](#library) enables users to create **messaging applications** and
**messaging components**. A *messaging application* is simply
a [microservice](https://en.wikipedia.org/wiki/Microservices) that controls the state of
*messaging component(s)*. A *messaging component* is an independent path of execution, specifically
a [coroutine](https://en.wikipedia.org/wiki/Coroutine), that
performs [reactive](https://www.reactive-streams.org/) messaging functionality. *Messaging
components* use [message queueing](https://en.wikipedia.org/wiki/Message_queue) to send and receive
messages, precisely, a sender sends messages to a *queue* and only *messaging component(s)*
receiving from the same *queue* are able to receive those messages.

**Kubernetes** is the mechanism to automate the deployment, scaling, and operation of *messaging
applications*. Kubernetes is essential to `connekted`, but the project **does not** support
Kubernetes cluster management, there are many fantastic managed Kubernetes offerings to choose from.
*Messaging application* Kubernetes resources are created, modified, and deleted by the
**messaging application operator**. The *messaging application operator* is an implementation of
the [operator pattern](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/).
Kubernetes' [custom resource control](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/)
extensibility enables automatic administration of *messaging application* infrastructure. To deploy
the *messaging application operator*, and subsequently
*messaging applications*, an existing Kubernetes (v1.20.0+) cluster is required.

## Usage

### Library

A *messaging application* is defined using
the [type-safe builders](https://kotlinlang.org/docs/reference/type-safe-builders.html) in
the [core library](https://search.maven.org/artifact/io.github.c-fraser/connekted-core). See
the [javadoc](https://www.javadoc.io/doc/io.github.c-fraser/connekted-core/latest/index.html)
for a complete reference of the API.

> Java 11+ is required to use the core library.

#### Messaging application

Initialize a *messaging application* through the `Connekted` object. A **messaging application**
may leverage any messaging technology through a [connekt](https://github.com/c-fraser/connekt)
[Transport](https://javadoc.io/doc/io.github.c-fraser/connekt-api/latest/io/github/cfraser/connekt/api/Transport.html)
implementation. This allows flexibility with regard to the underlying message delivery semantics and
decouples the *messaging application* from a particular messaging system. Within the
*messaging application* builder, a *messaging component* is added to the *messaging application*
through the use of the `addSender`, `addReceiver`, or `addSendingReceiver` function. After
*messaging application* initialization, the invocation of `run()` starts each of the added
*messaging components* and blocks the calling thread until *messaging application* shutdown.

<!--- PREFIX
@file:Suppress("PackageDirectoryMismatch")
-->

<!--- INCLUDE
import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport

fun main() {
----- SUFFIX
}
-->

```kotlin
Connekted(transport) {
  addSender<Nothing> { TODO() }
  addReceiver<Nothing> { TODO() }
  addSendingReceiver<Nothing, Nothing> { TODO() }
}
  .run()
```

<!--- KNIT example-01.kt -->

#### Messaging components

##### Sender

A sender is a *messaging component* that periodically sends messages to **receiver**
*messaging component(s)*.

<!--- PREFIX
@file:Suppress("PackageDirectoryMismatch")
-->

<!--- INCLUDE
import io.github.cfraser.connekted.FixedIntervalSchedule
import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport
import kotlinx.coroutines.flow.flowOf
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

fun main() {
  Connekted(transport) {
----- SUFFIX
  }
    .run()
}
-->

```kotlin
addSender {
  name = "example-sender"
  sendTo = "incrementing-integers"
  schedule = FixedIntervalSchedule(Duration.ofSeconds(3))
  with(AtomicInteger()) {
    send { flowOf(getAndIncrement().also { logger.info { "sending $it" } }) }
  }
  serialize { i -> ByteBuffer.allocate(4).apply { putInt(i) }.run { array() } }
}

```

<!--- KNIT example-02.kt --> 

##### Receiver

A receiver is a *messaging component* that that handles received messages.

<!--- PREFIX
@file:Suppress("PackageDirectoryMismatch")
-->

<!--- INCLUDE
import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport
import java.nio.ByteBuffer

fun main() {
  Connekted(transport) {
----- SUFFIX
  }
    .run()
}
-->

```kotlin
addReceiver {
  name = "example-receiver"
  receiveFrom = "incrementing-integers"
  onMessage { i -> logger.info { "received $i" } }
  deserialize { bytes -> ByteBuffer.wrap(bytes).int }
}
```

<!--- KNIT example-03.kt -->

##### Sending-receiver

A sending-receiver is a *messaging component* that receives messages and can send the result(s) of
processing each message to **receiver** *messaging component(s)*.

<!--- PREFIX
@file:Suppress("PackageDirectoryMismatch")
-->

<!--- INCLUDE
import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport
import kotlinx.coroutines.flow.flowOf
import org.apache.commons.lang3.StringUtils

fun main() {
  Connekted(transport) {
----- SUFFIX
  }
    .run()
}
-->

```kotlin
addSendingReceiver {
  name = "example-sending-receiver"
  receiveFrom = "random-strings"
  sendTo = "reversed-random-strings"
  onMessage { randomString -> flowOf(StringUtils.reverse(randomString)) }
  deserialize { bytes -> String(bytes) }
  serialize(String::toByteArray)
}
```

<!--- KNIT example-04.kt -->

### CLI

The CLI tool empowers users to manage the Kubernetes deployment and *messaging applications*.

#### Prerequisites

* Download the appropriate (for your OS and architecture) `connekted-cli` from the
  latest [release](https://github.com/c-fraser/connekted/releases).
    * Or download the `connekted-cli` fat/uber JAR and [install Java 11+](https://adoptopenjdk.net/)
      .
* Ensure the
  [kubeconfig file](https://kubernetes.io/docs/concepts/configuration/organize-cluster-access-kubeconfig/)
  contains access to desired Kubernetes cluster and is in the expected `$HOME/.kube` directory.

#### Deploy command

Deploy the *messaging application operator* on a Kubernetes cluster.

This command requires access to a docker repository, which must specified through the required
options. The command also requires the *images* zip archive (which can be downloaded from the
latest [release](https://github.com/c-fraser/connekted/releases)).

> The container image registry also stores the messaging application images.

Example usage:

```shell
connekted-cli deploy connekted-images-*.zip -r "$DOCKER_REPOSITORY" -u "$DOCKER_USERNAME" -p "$DOCKER_PASSWORD"
```

#### Run command

Run a messaging application on the [deployed](#deploy-command) Kubernetes cluster.

This command requires a messaging application fat/uber JAR.

> The given jar **must be** entirely self-contained, it will be *containerized* without the
> inclusion of any classpath dependencies.

Example usage:

```shell
connekted-cli run example-sender-01.jar
```

#### Get command

Get information about the running messaging application(s).

This command accepts one or more names and retrieves then displays the data for the corresponding
messaging application(s). If no name is provided then all messaging application data is retrieved
and displayed.

Example usage:

```shell
connekted-cli get example-receiver-01 example-sender-01
```

#### Watch command

Watch the logs of running messaging application(s).

This command accepts one or more names and watches the logs for the corresponding messaging
application(s).

Example usage:

```shell
connekted-cli watch example-receiver-01 example-sender-01
```

#### Stop command

Stop the messaging application(s).

This command accepts one or more names and stops the corresponding messaging application(s).

Example usage:

```shell
connekted-cli stop example-receiver-01 example-sender-01
```

#### Teardown command

Teardown the `connekted` deployment. Any running messaging applications will be stopped and then
the *
messaging application operator* is deleted from the Kubernetes cluster.

Example usage:

```shell
connekted-cli teardown
```

## Quickstart

Enable the [application](https://docs.gradle.org/current/userguide/application_plugin.html)
and [shadow](https://github.com/johnrengelman/shadow) plugins (to configure the creation of the
fat/uber JAR) and depend on
the [core library](https://github.com/c-fraser/connekted/tree/main/connekted-core)
in your `build.gradle`.

```groovy
plugins {
    id 'application'
    id 'com.github.johnrengelman.shadow' version '7.0.0'
}

application {
    mainClass = 'io.github.cfraser.connekted.e2e.test.MainKt'
}

dependencies {
    implementation 'io.github.c-fraser:connekted-core:+'
}
```

Create a *messaging application*.

<!--- PREFIX
@file:Suppress("PackageDirectoryMismatch")
-->

<!--- INCLUDE
import io.github.cfraser.connekted.FixedIntervalSchedule
import io.github.cfraser.connekted.Connekted
import io.github.cfraser.connekted.e2e.test.connekt.transport
import kotlinx.coroutines.flow.flow
import org.apache.commons.lang3.RandomStringUtils
import java.time.Duration
-->

```kotlin
fun main() {
  Connekted(transport) {
    addSender {
      name = "random-strings-sender"
      sendTo = "random-strings"
      schedule = FixedIntervalSchedule(Duration.ofSeconds(30))
      send {
        flow<String> {
          repeat(5) {
            val randomString = RandomStringUtils.randomAlphabetic(5)
            logger.info { "sending $randomString" }
            emit(randomString)
          }
        }
      }
      serialize(String::toByteArray)
    }

    addReceiver {
      name = "reversed-random-strings-receiver"
      receiveFrom = "reversed-random-strings"
      onMessage { message -> logger.info { "received $message" } }
      deserialize { bytes -> String(bytes) }
    }
  }
    .run()
}
```

<!--- KNIT example-05.kt --> 

> Refer to [this example](https://github.com/c-fraser/connekted/blob/main/e2e-test/src/main/java/io/github/cfraser/connekted/e2e/test/Example06.java)
> if you'd prefer implementing the *messaging application* in **Java**.

Build the fat/uber JAR for the *messaging application*.

```shell
./gradlew shadowJar
```

Deploy the *messaging application operator* on a Kubernetes cluster then run the
*messaging application* using
the [CLI](https://github.com/c-fraser/connekted/tree/main/connekted-cli).

```shell
connekted-cli deploy connekted-images-*.zip -r "$DOCKER_REPOSITORY" -u "$DOCKER_USERNAME" -p "$DOCKER_PASSWORD"
connekted-cli run messaing-application.jar
```

## License

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
