plugins { `java-library` }

dependencies {
  val fabric8KubernetesVersion: String by rootProject
  val kotlinLoggingVersion: String by rootProject

  api("io.fabric8:kubernetes-client:$fabric8KubernetesVersion")
  implementation("io.github.microutils:kotlin-logging-jvm:$kotlinLoggingVersion")
  implementation(project(":connekted-common"))

  testImplementation("io.fabric8:kubernetes-server-mock:$fabric8KubernetesVersion")
}
