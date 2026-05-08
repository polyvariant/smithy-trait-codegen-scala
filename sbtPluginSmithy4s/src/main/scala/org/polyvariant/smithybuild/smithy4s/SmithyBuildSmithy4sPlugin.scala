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

package org.polyvariant.smithybuild.smithy4s

import _root_.smithy4s.codegen.Smithy4sCodegenPlugin
import org.polyvariant.smithybuild.SmithyBuild
import org.polyvariant.smithybuild.SmithyBuildPlugin
import sbt.*

import Keys.*

/** Bridges [[SmithyBuildPlugin]] with smithy4s by populating `smithyBuildSettings` from the
  * project's smithy4s settings.
  *
  * Activate by enabling both plugins on a project; sources come from `smithy4sInputDirs` and
  * dependencies are taken from `libraryDependencies` filtered to entries in the `Smithy4s`
  * configuration. Resolvers fall through from the project's own `resolvers`.
  *
  * Users can still override `smithyBuildSettings` explicitly if they want to take over.
  */
object SmithyBuildSmithy4sPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = SmithyBuildPlugin && Smithy4sCodegenPlugin

  import SmithyBuildPlugin.autoImport.smithyBuildSettings
  import Smithy4sCodegenPlugin.autoImport.smithy4sInputDirs
  import Smithy4sCodegenPlugin.autoImport.Smithy4s

  override def projectSettings: Seq[Setting[?]] = Seq(
    smithyBuildSettings := SmithyBuild(
      sources = (Compile / smithy4sInputDirs).value,
      dependencies =
        libraryDependencies.value.filter(_.configurations.exists(_.contains(Smithy4s.name))),
      // Mirrors smithy4s's own GenerateSmithyBuild filter: include user-declared Maven
      // repositories, drop Maven Central (the IDE/Smithy CLI assume it implicitly).
      resolvers = resolvers.value.collect {
        case mr: MavenRepository if !mr.root.contains("repo1.maven.org") => mr
      },
    )
  )

}
