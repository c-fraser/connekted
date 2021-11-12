pluginManagement {
  val kotlinVersion: String by settings
  val spotlessVersion: String by settings
  val versionsVersion: String by settings
  val dokkaVersion: String by settings
  val nexusPublishVersion: String by settings
  val shadowVersion: String by settings
  val jibVersion: String by settings
  val springBootVersion: String by settings
  val detektVersion: String by settings
  val quarkusVersion: String by settings
  val jreleaserVersion: String by settings

  @Suppress("UnstableApiUsage")
  plugins {
    kotlin("jvm") version kotlinVersion
    kotlin("kapt") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion
    id("com.diffplug.spotless") version spotlessVersion
    id("com.github.ben-manes.versions") version versionsVersion
    id("org.jetbrains.dokka") version dokkaVersion
    id("io.github.gradle-nexus.publish-plugin") version nexusPublishVersion
    id("com.github.johnrengelman.shadow") version shadowVersion
    id("org.springframework.boot") version springBootVersion
    id("com.google.cloud.tools.jib") version jibVersion
    id("io.gitlab.arturbosch.detekt") version detektVersion
    id("io.quarkus") version quarkusVersion
    id("org.jreleaser") version jreleaserVersion
  }

  repositories {
    gradlePluginPortal()
    mavenCentral()
  }

  val atomicfuVersion: String by settings
  val knitVersion: String by settings

  resolutionStrategy {
    eachPlugin {
      when (requested.id.id) {
        "kotlinx-atomicfu" ->
            useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfuVersion")
        "kotlinx-knit" -> useModule("org.jetbrains.kotlinx:kotlinx-knit:$knitVersion")
      }
    }
  }
}

rootProject.name = "connekted"

include(
    "connekted-cli",
    "connekted-common",
    "connekted-core",
    "connekted-deploy",
    "connekted-docker",
    "connekted-k8s",
    "connekted-operator",
    "e2e-test")
