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

package org.polyvariant.smithyformat

import sbt.*
import sbt.plugins.JvmPlugin

import Keys.*

object SmithyFormatPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin

  object autoImport {

    val smithyFmtSourceDirectories = settingKey[Seq[File]](
      "Directories scanned for .smithy files to format"
    )

    val smithyFmt = taskKey[Unit](
      "Format Smithy IDL files in this configuration's source directories"
    )

    val smithyFmtCheck = taskKey[Unit](
      "Check whether Smithy IDL files are formatted; fails if any file would be reformatted"
    )

    val smithyFmtAll = taskKey[Unit](
      "Format Smithy IDL files in all configurations (Compile + Test)"
    )

    val smithyFmtCheckAll = taskKey[Unit](
      "Check Smithy IDL formatting in all configurations (Compile + Test)"
    )

  }

  import autoImport.*

  private def configScopedSettings(config: Configuration): Seq[Setting[?]] =
    inConfig(config)(
      Seq(
        smithyFmtSourceDirectories := Seq(
          (config / sourceDirectory).value / "smithy",
          (config / resourceDirectory).value / "META-INF" / "smithy",
        ),
        smithyFmt := {
          val log = streams.value.log
          val roots = smithyFmtSourceDirectories.value.map(_.toPath)
          val rewritten = SmithyFormat.formatAll(roots)
          if (rewritten.isEmpty)
            log.info(s"[smithyFmt] (${config.name}) no files needed formatting")
          else {
            rewritten.foreach(p => log.info(s"[smithyFmt] formatted ${p}"))
            log.info(s"[smithyFmt] (${config.name}) reformatted ${rewritten.size} file(s)")
          }
        },
        smithyFmtCheck := {
          val log = streams.value.log
          val roots = smithyFmtSourceDirectories.value.map(_.toPath)
          val unformatted = SmithyFormat.checkAll(roots)
          if (unformatted.nonEmpty) {
            unformatted.foreach(p => log.error(s"[smithyFmtCheck] not formatted: ${p}"))
            sys.error(
              s"[smithyFmtCheck] (${config.name}) ${unformatted.size} Smithy file(s) would be reformatted"
            )
          } else
            log.info(s"[smithyFmtCheck] (${config.name}) all Smithy files are formatted")
        },
      )
    )

  override def projectSettings: Seq[Setting[?]] =
    configScopedSettings(Compile) ++
      configScopedSettings(Test) ++
      Seq(
        smithyFmtAll := Def
          .sequential(
            Compile / smithyFmt,
            Test / smithyFmt,
          )
          .value,
        smithyFmtCheckAll := Def
          .sequential(
            Compile / smithyFmtCheck,
            Test / smithyFmtCheck,
          )
          .value,
      )

}
