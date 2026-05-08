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
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode

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

    val smithyDocGenOutputDirectory = settingKey[File](
      "The directory containing the generated documentation. " +
        "Defaults to smithyDocGenTargetDirectory / 'smithy-docgen-output'."
    )

    val smithyDocGenSettings = settingKey[ObjectNode](
      "The smithy-docgen settings ObjectNode. Defaults to a node derived from " +
        "smithyDocGenService and smithyDocGenFormat. Override with `:=` for full control, " +
        "or transform with `~=` to add/replace specific fields."
    )

  }

  import autoImport.*

  // Sbt-cache key: paths use PathRef so file content participates in the hash.
  // `settings` is the fully-resolved smithy-docgen settings node serialized to a
  // String, which collapses service/format/extra knobs into a single stable key.
  private case class CacheArgs(
    settings: String,
    outputDir: os.Path,
    smithySourcesDir: PathRef,
    dependencies: List[PathRef],
  )

  private object CacheArgs {

    import sjsonnew.*

    import BasicJsonProtocol.*

    private implicit val pathFormat: JsonFormat[os.Path] = BasicJsonProtocol
      .projectFormat[os.Path, File](p => p.toIO, file => os.Path(file))

    implicit val argsFmt: JsonFormat[CacheArgs] =
      caseClass(
        (
          settings: String,
          outputDir: os.Path,
          smithySourcesDir: PathRef,
          dependencies: List[PathRef],
        ) =>
          CacheArgs(
            settings,
            outputDir,
            smithySourcesDir,
            dependencies,
          ),
        (a: CacheArgs) =>
          Some(
            (
              a.settings,
              a.outputDir,
              a.smithySourcesDir,
              a.dependencies,
            )
          ),
      )(
        "settings",
        "outputDir",
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
      settings = Node.parse(cache.settings).expectObjectNode(),
      outputDir = cache.outputDir,
      smithySourcesDir = cache.smithySourcesDir.path,
      dependencies = cache.dependencies.map(_.path),
    )
    val out = SmithyDocGen.generate(core)
    Output(outputDir = out.outputDir.toIO)
  }

  override def projectSettings: Seq[Setting[?]] = Seq(
    smithyDocGenSourceDirectory := (Compile / resourceDirectory).value / "META-INF" / "smithy",
    smithyDocGenTargetDirectory := (Compile / target).value,
    smithyDocGenOutputDirectory := smithyDocGenTargetDirectory.value / "smithy-docgen-output",
    smithyDocGenFormat := Format.Markdown,
    smithyDocGenDependencies := Nil,
    smithyDocGenSettings := SmithyDocGen.buildSettings(
      smithyDocGenService.value,
      smithyDocGenFormat.value,
    ),
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
        settings = Node.printJson(smithyDocGenSettings.value),
        outputDir = os.Path(smithyDocGenOutputDirectory.value),
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
