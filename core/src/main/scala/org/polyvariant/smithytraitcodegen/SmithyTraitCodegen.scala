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

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.traitcodegen.TraitCodegenPlugin

object SmithyTraitCodegen {

  case class Args(
    javaPackage: String,
    smithyNamespace: String,
    targetDir: os.Path,
    smithySourcesDir: os.Path,
    dependencies: List[os.Path],
    externalProviders: List[String],
  )

  case class Output(metaDir: os.Path, javaDir: os.Path)

  def generate(args: Args): Output = {
    val outputDir = args.targetDir / "smithy-trait-generator-output"
    val genDir = outputDir / "java"
    val metaDir = outputDir / "meta"
    os.remove.all(outputDir)
    List(outputDir, genDir, metaDir).foreach(os.makeDir.all(_))

    val manifest = FileManifest.create(genDir.toNIO)

    val model = args
      .dependencies
      .foldLeft(Model.assembler().addImport(args.smithySourcesDir.toNIO)) { case (acc, dep) =>
        acc.addImport(dep.toNIO)
      }
      .assemble()
      .unwrap()

    val context = PluginContext
      .builder()
      .model(model)
      .fileManifest(manifest)
      .settings(
        // See TraitCodegenSettings for available fields
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

    // If there were no shapes to generate, this won't exist
    if (os.exists(genDir / "META-INF"))
      os.move(genDir / "META-INF", metaDir / "META-INF")

    os
      .walk(metaDir, includeTarget = true)
      .filter(os.isFile)
      .foreach { p =>
        if (p.last == "software.amazon.smithy.model.traits.TraitService") {
          args.externalProviders.foreach(provider => os.write.append(p, provider + "\n"))
        }
      }

    Output(metaDir = metaDir, javaDir = genDir)
  }

}
