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

package org.polyvariant.smithytraitcodegen

import sbt.*
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.traitcodegen.TraitCodegenPlugin

import java.io.File

object SmithyTraitCodegen {

  import sjsonnew.*

  import BasicJsonProtocol.*

  case class Args(
    javaPackage: String,
    smithyNamespace: String,
    targetDir: os.Path,
    smithySourcesDir: PathRef,
    dependencies: List[PathRef],
  )

  object Args {

    // format: off
    private type ArgsDeconstructed = String :*: String :*: os.Path :*: PathRef :*: List[PathRef] :*: LNil
    // format: on

    private implicit val pathFormat: JsonFormat[os.Path] = BasicJsonProtocol
      .projectFormat[os.Path, File](p => p.toIO, file => os.Path(file))

    implicit val argsIso = LList.iso[Args, ArgsDeconstructed](
      {
        args: Args => ("javaPackage", args.javaPackage) :*:
          ("smithyNamespace", args.smithyNamespace) :*:
          ("targetDir", args.targetDir) :*:
          ("smithySourcesDir", args.smithySourcesDir) :*:
          ("dependencies", args.dependencies) :*:
          LNil
      },
      {
        case (_, javaPackage) :*:
            (_, smithyNamespace) :*:
            (_, targetDir) :*:
            (_, smithySourcesDir) :*:
            (_, dependencies) :*:
            LNil =>
          Args(
            javaPackage = javaPackage,
            smithyNamespace = smithyNamespace,
            targetDir = targetDir,
            smithySourcesDir = smithySourcesDir,
            dependencies = dependencies,
          )
      },
    )

  }

  case class Output(metaDir: File, javaDir: File)

  object Output {

      // format: off
      private type OutputDeconstructed = File :*: File :*: LNil
      // format: on

    implicit val outputIso = LList.iso[Output, OutputDeconstructed](
      {
        output: Output => ("metaDir", output.metaDir) :*:
          ("javaDir", output.javaDir) :*:
          LNil
      },
      {
        case (_, metaDir) :*:
            (_, javaDir) :*:
            LNil =>
          Output(
            metaDir = metaDir,
            javaDir = javaDir,
          )
      },
    )

  }

  def generate(args: Args): Output = {
    val outputDir = args.targetDir / "smithy-trait-generator-output"
    val genDir = outputDir / "java"
    val metaDir = outputDir / "meta"
    os.remove.all(outputDir)
    List(outputDir, genDir, metaDir).foreach(os.makeDir.all(_))

    val manifest = FileManifest.create(genDir.toNIO)

    val model = args
      .dependencies
      .foldLeft(Model.assembler().addImport(args.smithySourcesDir.path.toNIO)) { case (acc, dep) =>
        acc.addImport(dep.path.toNIO)
      }
      .assemble()
      .unwrap()
    val context = PluginContext
      .builder()
      .model(model)
      .fileManifest(manifest)
      .settings(
        ObjectNode
          .builder()
          .withMember("package", args.javaPackage)
          .withMember("namespace", args.smithyNamespace)
          .withMember("header", ArrayNode.builder.build())
          .withMember("excludeTags", ArrayNode.builder.withValue("nocodegen").build())
          .build()
      )
      .build()
    val plugin = new TraitCodegenPlugin()
    plugin.execute(context)
    os.move(genDir / "META-INF", metaDir / "META-INF")
    Output(metaDir = metaDir.toIO, javaDir = genDir.toIO)
  }

}
