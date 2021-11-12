plugins {
  `java-library`
  `maven-publish`
  signing
  id("kotlinx-atomicfu")
}

atomicfu {
  val atomicfuVersion: String by rootProject

  dependenciesVersion = atomicfuVersion
  transformJvm = true
  variant = "VH"
  verbose = false
}

@Suppress("GradlePackageUpdate")
dependencies {
  val connektVersion: String by rootProject
  val cronUtilsVersion: String by rootProject
  val kotlinLoggingVersion: String by rootProject
  val kotlinxCoroutinesVersion: String by rootProject
  val reactiveStreamsVersion: String by rootProject
  val caffeineCacheVersion: String by rootProject
  val typesafeConfigVersion: String by rootProject
  val kotlinRetryVersion: String by rootProject
  val ktorVersion: String by rootProject
  val micrometerPrometheusVersion: String by rootProject
  val logbackVersion: String by rootProject
  val logbackJsonEncoderVersion: String by rootProject
  val commonsLang3Version: String by rootProject
  val jacksonKotlinVersion: String by rootProject
  val mockkVersion: String by rootProject

  api("io.github.c-fraser:connekt-api:$connektVersion")
  api("com.cronutils:cron-utils:$cronUtilsVersion")
  api("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
  api("org.reactivestreams:reactive-streams:$reactiveStreamsVersion")

  implementation(kotlin("stdlib-jdk8"))
  implementation("com.github.ben-manes.caffeine:caffeine:$caffeineCacheVersion")
  implementation("com.typesafe:config:$typesafeConfigVersion")
  implementation("com.michael-bull.kotlin-retry:kotlin-retry:$kotlinRetryVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$kotlinxCoroutinesVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:$kotlinxCoroutinesVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$kotlinxCoroutinesVersion")
  implementation("io.ktor:ktor-server-netty:$ktorVersion")
  implementation("io.ktor:ktor-auth:$ktorVersion")
  implementation("io.ktor:ktor-jackson:$ktorVersion")
  implementation("io.ktor:ktor-client-java:$ktorVersion")
  implementation("io.micrometer:micrometer-registry-prometheus:$micrometerPrometheusVersion")
  implementation("io.ktor:ktor-metrics-micrometer:$ktorVersion")
  implementation("io.ktor:ktor-client-auth:$ktorVersion")
  implementation("io.ktor:ktor-client-jackson:$ktorVersion")
  implementation("io.ktor:ktor-client-logging:$ktorVersion")
  implementation("ch.qos.logback:logback-classic:$logbackVersion")
  implementation("ch.qos.logback:logback-core:$logbackVersion")
  implementation("net.logstash.logback:logstash-logback-encoder:$logbackJsonEncoderVersion")
  implementation(project(":connekted-common"))

  testImplementation("io.github.c-fraser:connekt-local:$connektVersion")
  testImplementation("org.apache.commons:commons-lang3:$commonsLang3Version")
  testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonKotlinVersion")
  testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
  testImplementation("io.mockk:mockk:$mockkVersion")
}
