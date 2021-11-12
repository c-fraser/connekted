plugins {
  kotlin("kapt")
  kotlin("plugin.spring")
  id("org.springframework.boot")
  id("com.google.cloud.tools.jib")
}

apply(plugin = "io.spring.dependency-management")

val kotlinVersion: String by rootProject
val kotlinxCoroutinesVersion: String by rootProject
val okhttp3Version: String by rootProject

extra.apply {
  set("kotlin.version", kotlinVersion)
  set("kotlin-coroutines.version", kotlinxCoroutinesVersion)
  set("okhttp3.version", okhttp3Version)
}

kapt { includeCompileClasspath = false }

val operatorMainClassName = "io.github.cfraser.connekted.operator.OperatorApplicationKt"

springBoot { mainClass.set(operatorMainClassName) }

tasks { processResources { expand(project.properties) } }

jib {
  from { image = "gcr.io/distroless/java:11" }
  to {
    image = "${System.getenv("DOCKER_REPOSITORY")}:$name-$version"
    auth {
      username = System.getenv("DOCKER_USERNAME")
      password = System.getenv("DOCKER_PASSWORD")
    }
  }
  container {
    ports = listOf("8080")
    mainClass = operatorMainClassName
  }
}

@Suppress("GradlePackageUpdate")
dependencies {
  val javaOperatorSdkVersion: String by rootProject
  val kotlinLoggingVersion: String by rootProject
  val kotlinRetryVersion: String by rootProject
  val springfoxVersion: String by rootProject
  val fabric8KubernetesVersion: String by rootProject

  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")
  implementation("io.javaoperatorsdk:operator-framework:$javaOperatorSdkVersion")
  kapt("io.javaoperatorsdk:operator-framework:$javaOperatorSdkVersion")
  implementation("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
  implementation("com.michael-bull.kotlin-retry:kotlin-retry:$kotlinRetryVersion")
  implementation("io.springfox:springfox-boot-starter:$springfoxVersion")
  implementation(project(":connekted-k8s"))
  implementation(project(":connekted-common"))

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("com.squareup.okhttp3:mockwebserver:$okhttp3Version")
  testImplementation("io.fabric8:kubernetes-server-mock:$fabric8KubernetesVersion")
}
