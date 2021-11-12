plugins {
  `java-library`

  kotlin("plugin.serialization")
}

dependencies {
  val fabric8KubernetesVersion: String by rootProject
  val kotlinxSerializationJsonVersion: String by rootProject

  api("io.fabric8:kubernetes-client:$fabric8KubernetesVersion")
  implementation(
      "org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJsonVersion")
  implementation(project(":connekted-k8s"))
  implementation(project(":connekted-common"))

  testImplementation("io.fabric8:kubernetes-server-mock:$fabric8KubernetesVersion")
}
