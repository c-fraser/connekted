plugins {
  application

  id("com.github.johnrengelman.shadow")
}

application { mainClassName = "io.github.cfraser.connekted.e2e.test.runner.MainKt" }

dependencies {
  val connektVersion: String by rootProject
  val commonsLang3Version: String by rootProject
  val rxjava3Version: String by rootProject
  val ztExecVersion: String by rootProject

  implementation(project(":connekted-core"))
  implementation("io.github.c-fraser:connekt-nats:$connektVersion")
  implementation("org.apache.commons:commons-lang3:$commonsLang3Version")
  implementation("io.reactivex.rxjava3:rxjava:$rxjava3Version")

  testImplementation("org.zeroturnaround:zt-exec:$ztExecVersion")
}

tasks {
  shadowJar {
    dependsOn(rootProject.tasks.named("knit"))
    archiveClassifier.set(null as String?)
  }

  val testResources = "$buildDir/resources/test"

  val copyE2EJar by
      creating(Copy::class) {
        dependsOn(shadowJar)
        from("$buildDir/libs") {
          include("${project.name}-${project.version}.jar")
          rename { "e2e.jar" }
        }
        into(testResources)
      }

  val copyCliJar by
      creating(Copy::class) {
        val connektedCli = project(":connekted-cli")
        dependsOn(":connekted-cli:build")
        from(connektedCli.buildDir) {
          include("${connektedCli.name}-${connektedCli.version}-runner.jar")
          rename { "cli.jar" }
        }
        into(testResources)
      }

  val copyImagesZip by
      creating(Copy::class) {
        dependsOn(rootProject.tasks.named("createImagesZip"))
        from(rootProject.buildDir) {
          include("${rootProject.name}-images-${rootProject.version}.zip")
          rename { "images.zip" }
        }
        into(testResources)
      }

  create("e2eTest", Test::class) {
    dependsOn(copyE2EJar, copyCliJar, copyImagesZip)

    description = "Runs tests annotated with 'e2e' tag"

    useJUnitPlatform { includeTags("e2e") }
  }
}
