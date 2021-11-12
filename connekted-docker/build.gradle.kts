plugins { `java-library` }

dependencies {
  val jibCoreVersion: String by rootProject
  val kotlinxCoroutinesVersion: String by rootProject

  implementation("com.google.cloud.tools:jib-core:$jibCoreVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
  implementation(project(":connekted-common"))
}

tasks {
  val copyImagesZip by
      creating(Copy::class) {
        dependsOn(rootProject.tasks.named("createImagesZip"))
        from(rootProject.buildDir) {
          include("${rootProject.name}-images-${rootProject.version}.zip")
          rename { "images.zip" }
        }
        into("$buildDir/resources/test")
      }

  integrationTest { dependsOn(copyImagesZip) }
}
