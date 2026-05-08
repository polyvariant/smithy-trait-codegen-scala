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
import software.amazon.smithy.build.model.SmithyBuildConfig

import java.io.File
import java.util.Optional

import Keys.*

/** Provisions a synthetic sbt project per opted-in module that exposes the module's Smithy
  * sources and Maven dependencies to IDE imports.
  *
  * Users enable the plugin on a project and set `smithyBuildSettings` to describe the module:
  * {{{
  *   lazy val foo = project
  *     .enablePlugins(SmithyBuildPlugin)
  *     .settings(
  *       smithyBuildSettings := loadSmithyBuild().value, // reads ./smithy-build.json
  *       // or: smithyBuildSettings := loadSmithyBuild(file("custom-build.json")).value,
  *       // or build it inline / from libraryDependencies, etc.
  *     )
  * }}}
  *
  * For each project that enables this plugin, [[derivedProjects]] emits a synthetic project
  * named `<id>-smithyBuild` whose `unmanagedSourceDirectories`, `libraryDependencies`, and
  * `resolvers` come from the parent's `smithyBuildSettings` setting. Because the synthetic project is a
  * real sbt project, sbt's IDE integrations (IntelliJ sbt importer, Metals) treat it like any
  * other module — Smithy traits resolve, sources show up under their proper roots.
  *
  * The synthetic project's base directory lives under the parent's `target/` so it never
  * overlaps with the parent's content root. The project carries no compile output of its own —
  * it exists purely to expose source roots and a classpath to the IDE.
  */
object SmithyBuildPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = JvmPlugin

  /** Suffix appended to a parent project's id to form its synthetic IDE module's id. */
  val SyntheticIdSuffix = "-smithyBuild"

  /** Default filename of the build configuration consumed by [[autoImport.loadSmithyBuild]]. */
  val BuildConfigFileName = "smithy-build.json"

  object autoImport {

    val smithyBuildSettings = settingKey[SmithyBuild](
      "Sources, Maven dependencies, and resolvers exposed to the synthetic IDE module. " +
        "Set with `loadSmithyBuild().value` to read smithy-build.json, or build a SmithyBuild value directly."
    )

    /** Convenience: read `smithy-build.json` from the project base directory. */
    def loadSmithyBuild(): Def.Initialize[SmithyBuild] = Def.setting {
      readSmithyBuild(baseDirectory.value / BuildConfigFileName)
    }

    /** Read an explicit smithy-build.json file. Path is resolved as-is. */
    def loadSmithyBuild(configFile: File): Def.Initialize[SmithyBuild] = Def.setting {
      readSmithyBuild(configFile)
    }

  }

  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Seq(
    smithyBuildSettings := SmithyBuild.empty
  )

  /** For each project that activates this plugin, emit one synthetic IDE module. */
  override def derivedProjects(proj: ProjectDefinition[?]): Seq[Project] = {
    val parentId = proj.id
    val syntheticId = parentId + SyntheticIdSuffix
    val syntheticBase = proj.base / "target" / syntheticId

    val parent = LocalProject(parentId)
    Seq(
      Project(syntheticId, syntheticBase)
        .settings(
          name := syntheticId,
          description := s"Synthetic IDE module derived from $parentId/$BuildConfigFileName",
          crossPaths := false,
          autoScalaLibrary := false,
          Compile / unmanagedSourceDirectories := (parent / smithyBuildSettings).value.sources,
          Compile / unmanagedResourceDirectories := Nil,
          Compile / sources := Nil,
          Compile / managedSources := Nil,
          Compile / packageBin / publishArtifact := false,
          Compile / packageDoc / publishArtifact := false,
          Compile / packageSrc / publishArtifact := false,
          publish / skip := true,
          libraryDependencies := (parent / smithyBuildSettings).value.dependencies,
          resolvers := (parent / smithyBuildSettings).value.resolvers,
        )
    )
  }

  /** Read a smithy-build.json file from disk into a [[SmithyBuild]]. Missing files yield
    * [[SmithyBuild.empty]] so a typo in the path doesn't break the IDE import.
    *
    * The default-resolver fallback (Maven Central + local m2) is applied here, mirroring the
    * IntelliJ-side behavior in smithy-intellij-plugin#17 when `maven.repositories` is absent.
    */
  private def readSmithyBuild(configFile: File): SmithyBuild =
    if (!configFile.isFile) SmithyBuild.empty
    else {
      val config = SmithyBuildConfig.load(configFile.getAbsoluteFile.toPath)
      val sources = {
        val it = config.getSources.iterator()
        val b = List.newBuilder[String]
        while (it.hasNext) b += it.next()
        val it2 = config.getImports.iterator()
        while (it2.hasNext) b += it2.next()
        b.result().flatMap(resolveSourceRoot).distinct
      }
      val maven = optional(config.getMaven)
      val deps = maven
        .iterator
        .flatMap { m =>
          val it = m.getDependencies.iterator()
          val b = List.newBuilder[String]
          while (it.hasNext) b += it.next()
          b.result().iterator
        }
        .flatMap(parseModule)
        .toList
      val repos = maven.toList.flatMap { m =>
        val it = m.getRepositories.iterator()
        val b = List.newBuilder[Resolver]
        while (it.hasNext) {
          val r = it.next()
          val name = optional(r.getId).getOrElse(r.getUrl)
          b += MavenRepository(name, r.getUrl)
        }
        b.result()
      } match {
        case Nil => Seq(Resolver.mavenLocal, Resolver.DefaultMavenRepository)
        case rs  => rs
      }
      SmithyBuild(sources = sources, dependencies = deps, resolvers = repos)
    }

  /** Resolve a `sources`/`imports` entry to a directory suitable for an unmanaged source root.
    *
    * `SmithyBuildConfig.load` already resolves entries to absolute paths against the config
    * file's location, so we treat the input as a finished path. Directories pass through; files
    * fall back to their containing directory (mirroring the IntelliJ behavior — sbt source roots
    * must be directories). Missing entries are dropped, so a stale path in `smithy-build.json`
    * doesn't break IDE import.
    */
  private def resolveSourceRoot(raw: String): Option[File] = {
    val target = new File(raw)
    if (target.isDirectory) Some(target)
    else if (target.isFile) Option(target.getParentFile)
    else None
  }

  /** Parse a `groupId:artifactId:version` coordinate, ignoring malformed entries to keep IDE
    * import resilient against typos in `smithy-build.json`.
    */
  private def parseModule(coord: String): Option[ModuleID] =
    coord.split(":").toList match {
      case g :: a :: v :: Nil => Some(g % a % v)
      case _                  => None
    }

  private def optional[A](o: Optional[A]): Option[A] =
    if (o.isPresent) Some(o.get()) else None

}

/** What the plugin actually consumes when populating the synthetic IDE module.
  *
  * Build a value either with
  * [[org.polyvariant.smithybuild.SmithyBuildPlugin.autoImport.loadSmithyBuild]] (parses
  * `smithy-build.json`) or directly (e.g. derive sources from a directory and dependencies from
  * a filtered `libraryDependencies`).
  */
final case class SmithyBuild(
  sources: Seq[File],
  dependencies: Seq[ModuleID],
  resolvers: Seq[Resolver],
)

object SmithyBuild {
  val empty: SmithyBuild = SmithyBuild(Nil, Nil, Nil)
}
