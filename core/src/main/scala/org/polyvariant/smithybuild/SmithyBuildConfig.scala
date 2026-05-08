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

package org.polyvariant.smithybuild

import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode

import java.util.Optional

/** Minimal model of the parts of `smithy-build.json` that this plugin needs to provision an IDE
  * module: source roots and Maven dependency/repository declarations.
  *
  * See https://smithy.io/2.0/guides/smithy-build-json.html for the full schema.
  */
final case class SmithyBuildConfig(
  sources: List[String],
  imports: List[String],
  maven: Option[SmithyMavenConfig],
)

final case class SmithyMavenConfig(
  dependencies: List[String],
  repositories: List[SmithyMavenRepository],
)

final case class SmithyMavenRepository(
  url: String,
  id: Option[String],
)

object SmithyBuildConfig {

  def parse(json: String): SmithyBuildConfig = fromNode(
    Node.parseJsonWithComments(json).expectObjectNode()
  )

  def read(path: os.Path): SmithyBuildConfig = parse(os.read(path))

  private def fromNode(node: ObjectNode): SmithyBuildConfig = SmithyBuildConfig(
    sources = stringArray(node, "sources"),
    imports = stringArray(node, "imports"),
    maven = optional(node.getObjectMember("maven")).map(mavenFromNode(_)),
  )

  private def mavenFromNode(node: ObjectNode): SmithyMavenConfig = SmithyMavenConfig(
    dependencies = stringArray(node, "dependencies"),
    repositories = elements(optional(node.getArrayMember("repositories")))
      .map(_.expectObjectNode())
      .map(repoFromNode(_)),
  )

  private def repoFromNode(node: ObjectNode): SmithyMavenRepository = SmithyMavenRepository(
    url = node.expectStringMember("url").getValue,
    id = optional(node.getStringMember("id")).map(_.getValue),
  )

  private def stringArray(node: ObjectNode, member: String): List[String] =
    elements(optional(node.getArrayMember(member))).map(_.expectStringNode().getValue)

  private def elements(arr: Option[ArrayNode]): List[Node] =
    arr match {
      case None    => Nil
      case Some(a) =>
        val it = a.getElements.iterator()
        val b = List.newBuilder[Node]
        while (it.hasNext) b += it.next()
        b.result()
    }

  private def optional[A](o: Optional[A]): Option[A] =
    if (o.isPresent) Some(o.get()) else None

}
