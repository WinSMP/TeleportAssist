package org.winlogon.teleportassist

import io.papermc.paper.plugin.loader.{PluginClasspathBuilder, PluginLoader}
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

class ScalaLoader extends PluginLoader {
    override def classloader(classpathBuilder: PluginClasspathBuilder) = {
        val scalaVersion = "3.3.5"

        val resolver = MavenLibraryResolver()

        resolver.addRepository(
            RemoteRepository.Builder(
                "central",
                "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
            ).build()
        )

        resolver.addDependency(
            Dependency(
                DefaultArtifact(s"org.scala-lang:scala3-library_3:$scalaVersion"),
                null
            )
        )

        classpathBuilder.addLibrary(resolver)
    }
}
