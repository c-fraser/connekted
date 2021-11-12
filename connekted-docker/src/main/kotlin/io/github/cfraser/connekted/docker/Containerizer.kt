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
package io.github.cfraser.connekted.docker

import com.google.cloud.tools.jib.api.Containerizer as JibContainerizer
import com.google.cloud.tools.jib.api.ImageReference
import com.google.cloud.tools.jib.api.Jib
import com.google.cloud.tools.jib.api.RegistryImage
import com.google.cloud.tools.jib.api.TarImage
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer
import io.github.cfraser.connekted.common.Configs
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** A [Containerizer] interface is a type that can build and persist a container image. */
interface Containerizer {

  /**
   * Build and persist a container image which executes the jar at the given [jar].
   *
   * @param jar the path to the jar file which will be the entry point for the container
   * @param keystore the [Java Keystore](https://en.wikipedia.org/wiki/Java_KeyStore) to include in
   * the container and ultimately to be used by the JVM
   * @param options the
   * [Java Options](https://docs.oracle.com/en/java/javase/11/tools/java.html#GUID-3B1CE181-CD30-4178-9602-230B800D4FAE)
   * to launch the jar with
   * @return the reference to the image
   */
  suspend fun containerizeJar(jar: Path, keystore: Path?, options: Collection<String>): String

  /**
   * Persist the container images within the given [zip] file.
   *
   * @param zip the ZIP file containing tarred container images to persist
   * @return the image references
   */
  suspend fun containerizeImageZip(zip: Path): Set<String>

  companion object {

    /**
     * Factory function to initialize a [JibContainerizer].
     *
     * @param containerDestination the destination the container will be persisted to
     * @return the [JibContainerizer]
     */
    fun new(containerDestination: ContainerDestination): Containerizer =
        ContainerizerImpl(containerDestination)
  }
}

/**
 * [ContainerDestination] encapsulates the information necessary to write an image to a destination.
 *
 * @property repository the container repository for the image
 * @property username the username to use to access the container registry
 * @property password the password to use to access the container registry
 * @property type the external container registry
 */
data class ContainerDestination(
    val repository: String,
    val username: String? = null,
    val password: String? = null,
    val type: Type = Type.DOCKER_HUB
) {

  /** The supported image destinations. */
  enum class Type {
    DOCKER_HUB {

      override val serverUrl = "https://index.docker.io/v1/"
    };

    /** The URL to access the container registry server. */
    abstract val serverUrl: String
  }
}

/**
 * [ContainerizerImpl] is a [JibContainerizer] implementation which uses
 * [com.google.cloud.tools.jib] to build the container image.
 *
 * @property destination the registry/repository info used to store created image
 */
private class ContainerizerImpl(private val destination: ContainerDestination) : Containerizer {

  override suspend fun containerizeJar(
      jar: Path,
      keystore: Path?,
      options: Collection<String>
  ): String {
    withContext(Dispatchers.IO) {
      // verify that the jar is executable (manifest has `Main-Class` attribute)
      @Suppress("BlockingMethodInNonBlockingContext")
      JarFile(jar.toFile()).use { jarFile ->
        requireNotNull(jarFile.manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS))
      }
    }

    val appRoot = AbsoluteUnixPath.get("/app")
    val jarPath = appRoot.resolve(jar.fileName)
    val entrypoint =
        @OptIn(ExperimentalStdlibApi::class)
        buildList {
          add("java")
          for (option in options) add(option)
          add("-jar")
          add("$jarPath")
        }

    val jarFileEntryLayer = FileEntriesLayer.builder().setName("jar").addEntry(jar, jarPath).build()

    val containerBuilder =
        Jib.from("gcr.io/distroless/java:11")
            .setEntrypoint(entrypoint)
            .setFileEntriesLayers(jarFileEntryLayer)
            .apply {
              if (keystore != null) {
                val keystorePath = appRoot.resolve(Configs.keystoreFile)
                val keystoreFileEntryLayer =
                    FileEntriesLayer.builder()
                        .setName("keystore")
                        .addEntry(keystore, keystorePath)
                        .build()
                addFileEntriesLayer(keystoreFileEntryLayer)
              }
            }

    val containerizer = destination.containerizerFor(jar)

    return withContext(Dispatchers.IO) {
          @Suppress("BlockingMethodInNonBlockingContext")
          containerBuilder.containerize(containerizer)
        }
        .targetImage
        .toString()
  }

  override suspend fun containerizeImageZip(zip: Path): Set<String> {
    val imagesDirectory =
        @Suppress("BlockingMethodInNonBlockingContext")
        withContext(Dispatchers.IO) {
          Files.createTempDirectory("images").also { imagesDirectory ->
            ZipFile(zip.toFile()).use { zipFile ->
              for (entry in zipFile.entries()) {
                if (entry.isDirectory) continue
                val file = File(imagesDirectory.toFile(), entry.name).apply { parentFile.mkdirs() }
                zipFile.getInputStream(entry).use { `is` -> `is`.copyTo(file.outputStream()) }
              }
            }
          }
        }

    return coroutineScope {
      imagesDirectory
          .toFile()
          .listFiles()
          ?.map { image ->
            async(Dispatchers.IO) {
              val path = image.toPath()
              val containerBuilder = Jib.from(TarImage.at(path))
              val containerizer = destination.containerizerFor(path)
              withContext(Dispatchers.IO) {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    containerBuilder.containerize(containerizer)
                  }
                  .targetImage
                  .toString()
            }
          }
          ?.awaitAll()
          ?.toSet()
          ?: emptySet()
    }
  }

  companion object {

    /**
     * Initialize a [JibContainerizer] for the [ContainerDestination] and executable at [path].
     *
     * @param path the [Path] to the executable to be containerized
     * @return the [JibContainerizer]
     */
    private fun ContainerDestination.containerizerFor(path: Path): JibContainerizer {
      val name = path.toFile().nameWithoutExtension
      val uniqueSuffix = UUID.randomUUID().toString().take(5)
      val imageReference = ImageReference.of(type.toRegistry(), repository, "$name-$uniqueSuffix")
      val registryImage = RegistryImage.named(imageReference).addCredential(username, password)
      return JibContainerizer.to(registryImage)
    }

    /**
     * Convert the receiver [ContainerDestination.Type] to its [String] representation.
     *
     * @return the registry string
     */
    private fun ContainerDestination.Type.toRegistry(): String? =
        when (this) {
          // DockerHub is the default `ImageReference` registry so `null` can safely be passed
          ContainerDestination.Type.DOCKER_HUB -> null
        }
  }
}
