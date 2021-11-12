plugins {
  kotlin("plugin.allopen")
  kotlin("plugin.serialization")
  id("io.quarkus")
}

version = System.getProperty("connekted.release.tag").takeUnless { it.isNullOrBlank() } ?: version

allOpen {
  annotation("javax.enterprise.context.ApplicationScoped")
  annotation("io.quarkus.test.junit.QuarkusTest")
}

@Suppress("GradlePackageUpdate")
dependencies {
  val quarkusVersion: String by rootProject
  val cliktVersion: String by rootProject
  val mordantVersion: String by rootProject
  val klaxonVersion: String by rootProject
  val kotlinxCoroutinesVersion: String by rootProject
  val kotlinxSerializationJsonVersion: String by rootProject
  val kotlinLoggingVersion: String by rootProject
  val mockkVersion: String by rootProject

  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
  implementation("io.quarkus:quarkus-apache-httpclient")
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-jackson")
  implementation("io.quarkus:quarkus-kotlin")
  implementation("io.quarkus:quarkus-kubernetes-client")
  implementation("io.quarkus:quarkus-rest-client")
  implementation("io.quarkus:quarkus-rest-client-jackson")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
  implementation("com.github.ajalt.mordant:mordant:$mordantVersion")
  implementation("com.beust:klaxon:$klaxonVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
  implementation(
      "org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJsonVersion")
  implementation("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
  implementation(project(":connekted-k8s"))
  implementation(project(":connekted-deploy"))
  implementation(project(":connekted-common"))
  implementation(project(":connekted-docker")) { exclude(group = "commons-logging") }

  testImplementation("io.quarkus:quarkus-junit5")
  testImplementation("io.quarkus:quarkus-test-kubernetes-client")
  testImplementation("io.mockk:mockk:$mockkVersion")
}
