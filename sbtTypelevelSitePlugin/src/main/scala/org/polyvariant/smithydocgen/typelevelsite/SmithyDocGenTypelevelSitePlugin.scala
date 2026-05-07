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

package org.polyvariant.smithydocgen.typelevelsite

import laika.ast.Path.Root
import laika.config.LinkValidation
import laika.io.model.FilePath
import laika.sbt.LaikaPlugin
import org.polyvariant.smithydocgen.SmithyDocGen
import org.polyvariant.smithydocgen.SmithyDocGenPlugin
import org.typelevel.sbt.TypelevelSitePlugin
import sbt.*

import java.io.File

object SmithyDocGenTypelevelSitePlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = TypelevelSitePlugin && SmithyDocGenPlugin

  import SmithyDocGenPlugin.autoImport.*
  import LaikaPlugin.autoImport.*

  object autoImport {

    val smithyDocGenSitePath = settingKey[String](
      "Virtual path under which the generated smithy docs are mounted in the Laika site. " +
        "Defaults to 'smithy', so docs land at /smithy/."
    )

    val smithyDocGenSiteContentDirectory = settingKey[File](
      "The directory whose contents Laika will mount under smithyDocGenSitePath. " +
        "Defaults to <outputDir>/build/markdown for SphinxMarkdown formats and " +
        "<outputDir>/content for plain Markdown."
    )

  }

  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Seq(
    smithyDocGenSitePath := "smithy",
    // Default to plain markdown so users don't need Python/Sphinx. The output is
    // raw MyST-flavored markdown — Laika passes most of it through; some directives
    // may render literally. Users wanting cleaner output can opt into
    //   smithyDocGenFormat := Format.SphinxMarkdown(sphinxFormat = Some("markdown"), autoBuild = true)
    // (which adds a Python toolchain requirement at build time).
    smithyDocGenSiteContentDirectory := {
      val out = smithyDocGenOutputDirectory.value
      smithyDocGenFormat.value match {
        case _: SmithyDocGen.Format.SphinxMarkdown => out / "build" / "markdown"
        case SmithyDocGen.Format.Markdown          => out / "content"
      }
    },
    laikaInputs := laikaInputs
      .value
      .delegate
      .addDirectory(
        FilePath.fromJavaFile(smithyDocGenSiteContentDirectory.value),
        Root / smithyDocGenSitePath.value,
      ),
    // smithy-docgen markdown contains internal cross-references that Laika's
    // strict link validator can't resolve (anchor casing differs, files outside
    // its parser scope, etc.). Exclude the mounted subtree from validation.
    laikaConfig := laikaConfig
      .value
      .withConfigValue(
        LinkValidation.Global(excluded = Seq(Root / smithyDocGenSitePath.value))
      ),
    // Make sure the docgen task has run (and its output exists on disk) before
    // Laika reads the site.
    laikaSite := laikaSite.dependsOn(SmithyDocGenPlugin.Keys.generateSmithyDocs).value,
  )

}
