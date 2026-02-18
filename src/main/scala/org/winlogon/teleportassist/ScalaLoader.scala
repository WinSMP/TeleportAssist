// SPDX-License-Identifier: MPL-2.0
package org.winlogon.teleportassist

import io.papermc.paper.plugin.loader.{PluginClasspathBuilder, PluginLoader}
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

class ScalaLoader extends PluginLoader {
    /** Adds Scala stdlib, SQLite JDBC, and AsyncCraftr to the plugin classpath at load time. */
    override def classloader(classpathBuilder: PluginClasspathBuilder) = {
        val resolver = MavenLibraryResolver()

        resolver.addRepository(
            RemoteRepository.Builder(
                "central", "default",
                MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR
            ).build()
        )

        resolver.addRepository(
            RemoteRepository.Builder(
                "winlogon-code-releases", "default",
                "https://maven.winlogon.org/releases"
            ).build()
        )

        resolver.addDependency(
            Dependency(DefaultArtifact("org.scala-lang:scala3-library_3:3.3.7"), null)
        )

        resolver.addDependency(
            Dependency(DefaultArtifact("org.xerial:sqlite-jdbc:3.49.1.0"), null)
        )

        resolver.addDependency(
            Dependency(DefaultArtifact("org.winlogon:asynccraftr:0.2.0"), null)
        )

        classpathBuilder.addLibrary(resolver)
    }
}
