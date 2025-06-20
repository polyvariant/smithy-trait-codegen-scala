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
import software.amazon.smithy.model.transform.ModelTransformer
import java.util.stream.Collectors
import scala.collection.JavaConverters.*

object SmithyTraitCodegen {

  import sjsonnew.*

  import BasicJsonProtocol.*

  case class Args(
    javaPackage: String,
    smithyNamespace: String,
    targetDir: os.Path,
    smithySourcesDir: PathRef,
    dependencies: List[PathRef],
    externalProviders: List[String],
  )

  object Args {

    // format: off
    private type ArgsDeconstructed = String :*: String :*: os.Path :*: PathRef :*: List[PathRef] :*: List[String] :*: LNil
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
          ("externalProviders", args.externalProviders) :*:
          LNil
      },
      {
        case (_, javaPackage) :*:
            (_, smithyNamespace) :*:
            (_, targetDir) :*:
            (_, smithySourcesDir) :*:
            (_, dependencies) :*:
            (_, externalProviders) :*:
            LNil =>
          Args(
            javaPackage = javaPackage,
            smithyNamespace = smithyNamespace,
            targetDir = targetDir,
            smithySourcesDir = smithySourcesDir,
            dependencies = dependencies,
            externalProviders = externalProviders,
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

  // Hack / workaround for https://github.com/smithy-lang/smithy/pull/2671
  private def namespaceHackRequired(ns: String) =
    ns.startsWith("smithy") && ns != "smithy" && !ns.startsWith("smithy.")

  private def renameNamespaceForHack(ns: String) =
    if (namespaceHackRequired(ns))
      "hack" + ns
    else
      ns

  private def replaceNamespaceRefsInFile(fileText: String, ns: String) =
    if (namespaceHackRequired(ns)) {
      fileText.replaceAll(s"hack$ns", ns)
    } else {
      fileText
    }

  def generate(args: Args): Output = {
    val outputDir = args.targetDir / "smithy-trait-generator-output"
    val genDir = outputDir / "java"
    val metaDir = outputDir / "meta"
    os.remove.all(outputDir)
    List(outputDir, genDir, metaDir).foreach(os.makeDir.all(_))

    val manifest = FileManifest.create(genDir.toNIO)

    val model =
      args
        .dependencies
        .foldLeft(Model.assembler().addImport(args.smithySourcesDir.path.toNIO)) {
          case (acc, dep) => acc.addImport(dep.path.toNIO)
        }
        .assemble()
        .unwrap() match {
        case model =>
          if (namespaceHackRequired(args.smithyNamespace)) {
            println("Applying namespace workaround - `hack` prefix will be used")

            val renames =
              model
                .shapes()
                .collect(Collectors.toList())
                .asScala
                .filter(_.getId().getNamespace() == args.smithyNamespace)
                .map { shp =>
                  shp.getId() ->
                    shp.getId().withNamespace(renameNamespaceForHack(shp.getId().getNamespace()))
                }
                .toMap
                .asJava

            ModelTransformer
              .create()
              .renameShapes(model, renames)
          } else
            model
      }

    val context = PluginContext
      .builder()
      .model(model)
      .fileManifest(manifest)
      .settings(
        // See TraitCodegenSettings for available fields
        ObjectNode
          .builder()
          .withMember("package", args.javaPackage)
          .withMember("namespace", renameNamespaceForHack(args.smithyNamespace))
          .withMember("header", ArrayNode.builder.build())
          .withMember("excludeTags", ArrayNode.builder.withValue("nocodegen").build())
          .build()
      )
      .build()
    val plugin = new TraitCodegenPlugin()
    plugin.execute(context)

    // If there were no shapes to generate, this won't exist
    if (os.exists(genDir / "META-INF"))
      os.move(genDir / "META-INF", metaDir / "META-INF")

    os.walk(genDir)
      .filter(os.isFile)
      .filter(_.ext == "java")
      .foreach { f =>
        os.write.over(f, replaceNamespaceRefsInFile(os.read(f), args.smithyNamespace))
      }

    os
      .walk(metaDir, includeTarget = true)
      .filter(os.isFile)
      .foreach { p =>
        if (p.toIO.name == "software.amazon.smithy.model.traits.TraitService") {
          args.externalProviders.foreach(provider => os.write.append(p, provider + "\n"))
        }
      }

    Output(metaDir = metaDir.toIO, javaDir = genDir.toIO)
  }

}
