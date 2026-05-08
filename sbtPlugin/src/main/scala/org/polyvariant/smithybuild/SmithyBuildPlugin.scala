/*
 * Copyright 2025 Polyvariant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polyvariant.smithybuild

import sbt.*
import sbt.plugins.JvmPlugin

import java.io.File

import Keys.*

/** Provisions a synthetic sbt project named `smithy-build-ide` from the contents of
  * `smithy-build.json` at the build root.
  *
  * The synthetic project mirrors the dependency-and-source-root half of the IntelliJ
  * "Smithy: smithy-build" module: each `sources`/`imports` entry becomes an unmanaged source
  * directory and each `maven.dependencies` entry becomes a `libraryDependency`. When
  * `maven.repositories` is non-empty its entries are used as resolvers; otherwise we fall back to
  * Maven Central and the local m2 cache.
  *
  * Sbt's IDE integrations (IntelliJ's sbt importer, Metals, etc.) treat the synthetic project as
  * any other module, so Smithy sources resolve their declared trait dependencies without any
  * IDE-specific plumbing.
  *
  * The synthetic project's base directory lives under `target/` so it never overlaps with the
  * root project's `.` content root. The project carries no compile output of its own — it exists
  * purely to expose source roots and a classpath to the IDE.
  */
object SmithyBuildPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = JvmPlugin

  /** Identifier of the synthetic sbt project this plugin contributes. */
  val SyntheticProjectId = "smithy-build-ide"

  /** Filename of the build configuration consumed by this plugin. */
  val BuildConfigFileName = "smithy-build.json"

  override def extraProjects: Seq[Project] = {
    // sbt's loader runs with the build's base directory as CWD, so this resolves to the
    // project root the user invoked sbt from.
    val baseDir = new File(".").getAbsoluteFile.getParentFile
    val configFile = new File(baseDir, BuildConfigFileName)
    if (!configFile.isFile) Nil
    else {
      val config = SmithyBuildConfig.read(os.Path(configFile))
      Seq(buildSyntheticProject(baseDir, config))
    }
  }

  private def buildSyntheticProject(baseDir: File, config: SmithyBuildConfig): Project = {
    val sourceRoots = (config.sources ++ config.imports)
      .flatMap(resolveSourceRoot(baseDir, _))
      .distinct

    val deps = config
      .maven
      .map(_.dependencies)
      .getOrElse(Nil)
      .flatMap(parseModule)

    val repos = config.maven.map(_.repositories).getOrElse(Nil) match {
      case Nil  => Seq(Resolver.mavenLocal, Resolver.DefaultMavenRepository)
      case repo =>
        repo.map(r => MavenRepository(r.id.getOrElse(r.url), r.url))
    }

    val syntheticBase = new File(baseDir, s"target/$SyntheticProjectId")
    Project(SyntheticProjectId, syntheticBase)
      .settings(
        name := SyntheticProjectId,
        description := s"Synthetic IDE module derived from $BuildConfigFileName",
        crossPaths := false,
        autoScalaLibrary := false,
        Compile / unmanagedSourceDirectories := sourceRoots,
        Compile / unmanagedResourceDirectories := Nil,
        Compile / sources := Nil,
        Compile / managedSources := Nil,
        Compile / packageBin / publishArtifact := false,
        Compile / packageDoc / publishArtifact := false,
        Compile / packageSrc / publishArtifact := false,
        publish / skip := true,
        libraryDependencies ++= deps,
        resolvers := repos,
      )
  }

  /** Resolve a `sources`/`imports` entry to a directory suitable for an unmanaged source root.
    *
    * Directories pass through; files fall back to their containing directory (mirroring the
    * IntelliJ behavior — sbt source roots must be directories). Missing entries are dropped, on
    * the assumption that a stale path in `smithy-build.json` shouldn't break IDE import.
    */
  private def resolveSourceRoot(baseDir: File, raw: String): Option[File] = {
    val normalized = raw.trim.stripPrefix("./")
    if (normalized.isEmpty) None
    else {
      val target = new File(baseDir, normalized)
      if (target.isDirectory) Some(target)
      else if (target.isFile) Option(target.getParentFile)
      else None
    }
  }

  /** Parse a `groupId:artifactId:version` coordinate, ignoring malformed entries to keep IDE
    * import resilient against typos in `smithy-build.json`.
    */
  private def parseModule(coord: String): Option[ModuleID] =
    coord.split(":").toList match {
      case g :: a :: v :: Nil => Some(g % a % v)
      case _                  => None
    }

}
