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

package org.polyvariant.smithydocgen

import org.polyvariant.smithytraitcodegen.PathRef
import sbt.*
import sbt.plugins.JvmPlugin

import java.io.File

import Keys.*

object SmithyDocGenPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = JvmPlugin

  // Re-export the core Format ADT so users can write
  // `smithyDocGenFormat := SmithyDocGenPlugin.Format.Markdown`.
  type Format = SmithyDocGen.Format
  val Format: SmithyDocGen.Format.type = SmithyDocGen.Format

  object autoImport {

    val smithyDocGenService = settingKey[String](
      "The shape ID of the service to document (e.g. com.example#MyService)"
    )

    val smithyDocGenFormat = settingKey[Format](
      "Output format for smithy-docgen. Defaults to Format.Markdown. " +
        "Format.SphinxMarkdown(autoBuild = true) requires Python 3 on PATH and network access."
    )

    val smithyDocGenDependencies = settingKey[List[ModuleID]](
      "Dependencies to be added into the docgen model"
    )

    val smithyDocGenSourceDirectory = settingKey[File](
      "The directory where the smithy sources are located"
    )

    val smithyDocGenTargetDirectory = settingKey[File](
      "The directory where the generated documentation will be placed"
    )

  }

  import autoImport.*

  // Sbt-cache key: like SmithyDocGen.Args, but path fields use PathRef so that
  // file content participates in the cache hash. The runtime call into the core
  // docgen converts this back to a plain SmithyDocGen.Args.
  private case class CacheArgs(
    service: String,
    format: Format,
    targetDir: os.Path,
    smithySourcesDir: PathRef,
    dependencies: List[PathRef],
  )

  private object CacheArgs {

    import sjsonnew.*

    import BasicJsonProtocol.*

    private implicit val pathFormat: JsonFormat[os.Path] = BasicJsonProtocol
      .projectFormat[os.Path, File](p => p.toIO, file => os.Path(file))

    // Cache hashing only — formats are flattened to a stable string representation.
    // SphinxMarkdown's nested options are part of the hash so they invalidate the cache
    // when changed.
    private implicit val formatFmt: JsonFormat[Format] = BasicJsonProtocol
      .projectFormat[Format, String](
        {
          case SmithyDocGen.Format.Markdown          => "markdown"
          case s: SmithyDocGen.Format.SphinxMarkdown =>
            "sphinx-markdown" +
              s";sphinxFormat=${s.sphinxFormat.getOrElse("")}" +
              s";theme=${s.theme.getOrElse("")}" +
              s";extraDependencies=${s.extraDependencies.mkString(",")}" +
              s";extraExtensions=${s.extraExtensions.mkString(",")}" +
              s";autoBuild=${s.autoBuild}"
        },
        // Cache hashing only — never round-tripped to a Format value.
        _ => sys.error("Format JsonFormat is hash-only and not deserializable"),
      )

    implicit val argsFmt: JsonFormat[CacheArgs] =
      caseClass(
        (
          service: String,
          format: Format,
          targetDir: os.Path,
          smithySourcesDir: PathRef,
          dependencies: List[PathRef],
        ) =>
          CacheArgs(
            service,
            format,
            targetDir,
            smithySourcesDir,
            dependencies,
          ),
        (a: CacheArgs) =>
          Some(
            (
              a.service,
              a.format,
              a.targetDir,
              a.smithySourcesDir,
              a.dependencies,
            )
          ),
      )(
        "service",
        "format",
        "targetDir",
        "smithySourcesDir",
        "dependencies",
      )

  }

  // sbt-shaped Output (File-based) so it can be persisted by the sbt cache.
  case class Output(outputDir: File)

  object Output {

    import sjsonnew.*

    // format: off
    private type OutputDeconstructed = File :*: LNil
    // format: on

    implicit val outputIso: IsoLList.Aux[Output, OutputDeconstructed] = LList
      .iso[Output, OutputDeconstructed](
        (output: Output) =>
          ("outputDir", output.outputDir) :*:
            LNil,
        {
          case (_, outputDir) :*:
              LNil =>
            Output(outputDir = outputDir)
        },
      )

  }

  private def runDocGen(cache: CacheArgs): Output = {
    val core = SmithyDocGen.Args(
      service = cache.service,
      format = cache.format,
      targetDir = cache.targetDir,
      smithySourcesDir = cache.smithySourcesDir.path,
      dependencies = cache.dependencies.map(_.path),
    )
    val out = SmithyDocGen.generate(core)
    Output(outputDir = out.outputDir.toIO)
  }

  override def projectSettings: Seq[Setting[?]] = Seq(
    smithyDocGenSourceDirectory := (Compile / resourceDirectory).value / "META-INF" / "smithy",
    smithyDocGenTargetDirectory := (Compile / target).value,
    smithyDocGenFormat := Format.Markdown,
    smithyDocGenDependencies := Nil,
    Keys.generateSmithyDocs := Def.task {
      import sbt.util.CacheImplicits.*
      implicit val docgenCache: sbt.util.Cache[CacheArgs, Output] = new sbt.util.BasicCache()
      val s = (Compile / streams).value

      val report = update.value
      val dependencies = smithyDocGenDependencies.value
      val jars = dependencies.flatMap(m =>
        report.matching(
          moduleFilter(organization = m.organization, name = m.name, revision = m.revision)
        )
      )
      require(
        jars.size == dependencies.size,
        "Not all dependencies required for smithy-docgen have been found",
      )

      val cacheArgs = CacheArgs(
        service = smithyDocGenService.value,
        format = smithyDocGenFormat.value,
        targetDir = os.Path(smithyDocGenTargetDirectory.value),
        smithySourcesDir = PathRef(smithyDocGenSourceDirectory.value),
        dependencies = jars.map(PathRef(_)).toList,
      )

      val cachedDocGen =
        Cache.cached(s.cacheStoreFactory.make("smithy-docgen")) {
          runDocGen
        }

      cachedDocGen(cacheArgs)
    }.value,
    libraryDependencies ++= smithyDocGenDependencies.value,
  )

  object Keys {

    val generateSmithyDocs = taskKey[Output](
      "Run AWS smithy-docgen on the Smithy model and produce documentation"
    )

  }

}
