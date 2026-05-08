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

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.docgen.SmithyDocPlugin
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode

object SmithyDocGen {

  sealed trait Format extends Product with Serializable {
    def name: String
  }

  object Format {

    case object Markdown extends Format {
      val name: String = "markdown"
    }

    /** See https://github.com/smithy-lang/smithy/tree/main/smithy-docgen for the underlying
      * options.
      *
      * @param sphinxFormat
      *   the Sphinx output format (e.g. "html", "dirhtml"). Defaults to "html" upstream.
      * @param theme
      *   the Sphinx theme. Defaults to "furo" upstream.
      * @param extraDependencies
      *   extra entries to add to the generated `requirements.txt`.
      * @param extraExtensions
      *   extra Sphinx extensions to enable in `conf.py`.
      * @param autoBuild
      *   whether to automatically render the docs to the output format. Defaults to `false` in this
      *   plugin (upstream default is `true`). When enabled, requires Python 3 on `PATH` and network
      *   access.
      */
    case class SphinxMarkdown(
      sphinxFormat: Option[String] = None,
      theme: Option[String] = None,
      extraDependencies: List[String] = Nil,
      extraExtensions: List[String] = Nil,
      autoBuild: Boolean = false,
    ) extends Format {
      val name: String = "sphinx-markdown"
    }

  }

  case class Args(
    settings: ObjectNode,
    outputDir: os.Path,
    smithySourcesDir: os.Path,
    dependencies: List[os.Path],
  )

  case class Output(outputDir: os.Path)

  def generate(args: Args): Output = {
    val outputDir = args.outputDir
    os.remove.all(outputDir)
    os.makeDir.all(outputDir)

    val manifest = FileManifest.create(outputDir.toNIO)
    val sharedManifest = FileManifest.create(outputDir.toNIO)

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
      .sharedFileManifest(sharedManifest)
      .settings(args.settings)
      .build()
    val plugin = new SmithyDocPlugin()
    plugin.execute(context)

    Output(outputDir = outputDir)
  }

  /** Build the settings ObjectNode from a typed `service` and `Format`. Callers that want to tweak
    * fields the typed API doesn't expose can take the result and merge in their own members.
    *
    * See `software.amazon.smithy.docgen.DocSettings` for available top-level fields.
    */
  def buildSettings(service: String, format: Format): ObjectNode = {
    val base = ObjectNode
      .builder()
      .withMember("service", service)
      .withMember("format", format.name)

    format match {
      case Format.Markdown          => base.build()
      case s: Format.SphinxMarkdown =>
        val sphinx = sphinxNode(s)
        if (sphinx.isEmpty)
          base.build()
        else
          base
            .withMember(
              "integrations",
              ObjectNode.builder().withMember("sphinx", sphinx).build(),
            )
            .build()
    }
  }

  private def sphinxNode(s: Format.SphinxMarkdown): ObjectNode = {
    val b = ObjectNode.builder()
    s.sphinxFormat.foreach(v => b.withMember("format", v))
    s.theme.foreach(v => b.withMember("theme", v))
    if (s.extraDependencies.nonEmpty)
      b.withMember("extraDependencies", stringArray(s.extraDependencies))
    if (s.extraExtensions.nonEmpty)
      b.withMember("extraExtensions", stringArray(s.extraExtensions))
    b.withMember("autoBuild", s.autoBuild)
    b.build()
  }

  private def stringArray(values: List[String]): ArrayNode = {
    val b = ArrayNode.builder()
    values.foreach(v => b.withValue(Node.from(v)))
    b.build()
  }

}
