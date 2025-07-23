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
import sbt.plugins.JvmPlugin

import Keys.*

object SmithyTraitCodegenPlugin extends AutoPlugin {
  override def trigger: PluginTrigger = noTrigger
  override def requires: Plugins = JvmPlugin

  object autoImport {

    val smithyTraitCodegenJavaPackage = settingKey[String](
      "The java target package where the generated smithy traits will be created"
    )

    val smithyTraitCodegenNamespace = settingKey[String](
      "The smithy namespace where the traits are defined"
    )

    val smithyTraitCodegenDependencies = settingKey[List[ModuleID]](
      "Dependencies to be added into codegen model"
    )

    val smithyTraitCodegenSourceDirectory = settingKey[File](
      "The directory where the smithy sources are located"
    )

    val smithyTraitCodegenTargetDirectory = settingKey[File](
      "The directory where the generated Java sources and resources will be placed"
    )

    val smithyTraitCodegenExternalProviders = settingKey[List[String]](
      "External trait provideres"
    )

  }

  import autoImport.*

  override def projectSettings: Seq[Setting[?]] = Seq(
    smithyTraitCodegenSourceDirectory := (Compile / resourceDirectory).value / "META-INF" / "smithy",
    smithyTraitCodegenTargetDirectory := (Compile / target).value,
    smithyTraitCodegenExternalProviders := Nil,
    Keys.generateSmithyTraits := Def.task {
      import sbt.util.CacheImplicits.*
      val s = (Compile / streams).value

      val report = update.value
      val dependencies = smithyTraitCodegenDependencies.value
      val jars = dependencies.flatMap(m =>
        report.matching(
          moduleFilter(organization = m.organization, name = m.name, revision = m.revision)
        )
      )
      require(
        jars.size == dependencies.size,
        "Not all dependencies required for smithy-trait-codegen have been found",
      )

      val args = SmithyTraitCodegen.Args(
        javaPackage = smithyTraitCodegenJavaPackage.value,
        smithyNamespace = smithyTraitCodegenNamespace.value,
        targetDir = os.Path(smithyTraitCodegenTargetDirectory.value),
        smithySourcesDir = PathRef(smithyTraitCodegenSourceDirectory.value),
        dependencies = jars.map(PathRef(_)).toList,
        externalProviders = smithyTraitCodegenExternalProviders.value,
      )

      val cachedCodegen =
        Cache.cached(s.cacheDirectory) {
          SmithyTraitCodegen.generate
        }

      cachedCodegen(args)
    }.value,
    Compile / sourceGenerators += Def.task {
      val codegenOutput = (Compile / Keys.generateSmithyTraits).value
      cleanCopy(source = codegenOutput.javaDir, target = (Compile / sourceManaged).value / "java")
    },
    Compile / resourceGenerators += Def.task {
      val codegenOutput = (Compile / Keys.generateSmithyTraits).value
      cleanCopy(source = codegenOutput.metaDir, target = (Compile / resourceManaged).value)
    }.taskValue,
    libraryDependencies ++= smithyTraitCodegenDependencies.value,
  )

  private def cleanCopy(source: File, target: File) = {
    val sourcePath = os.Path(source)
    val targetPath = os.Path(target)
    os.remove.all(targetPath)
    os.copy(from = sourcePath, to = targetPath, createFolders = true)
    os.walk(targetPath).map(_.toIO).filter(_.isFile())
  }

  object Keys {

    val generateSmithyTraits = taskKey[SmithyTraitCodegen.Output](
      "Run AWS smithy-trait-codegen on the protocol specs"
    )

  }

}
