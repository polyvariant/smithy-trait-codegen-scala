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

import munit.FunSuite

class SmithyBuildConfigTest extends FunSuite {

  test("parses sources, imports, and full maven block") {
    val parsed = SmithyBuildConfig.parse(
      """{
        |  "version": "1.0",
        |  "sources": ["model", "src/main/smithy"],
        |  "imports": ["external.json"],
        |  "maven": {
        |    "dependencies": ["software.amazon.smithy:smithy-trait-codegen:1.68.0"],
        |    "repositories": [
        |      {"id": "central", "url": "https://repo1.maven.org/maven2/"},
        |      {"url": "https://example.com/repo"}
        |    ]
        |  }
        |}""".stripMargin
    )

    assertEquals(parsed.sources, List("model", "src/main/smithy"))
    assertEquals(parsed.imports, List("external.json"))
    assertEquals(
      parsed.maven,
      Some(
        SmithyMavenConfig(
          dependencies = List("software.amazon.smithy:smithy-trait-codegen:1.68.0"),
          repositories = List(
            SmithyMavenRepository(url = "https://repo1.maven.org/maven2/", id = Some("central")),
            SmithyMavenRepository(url = "https://example.com/repo", id = None),
          ),
        )
      ),
    )
  }

  test("absent maven, sources and imports default to empty") {
    val parsed = SmithyBuildConfig.parse("""{"version": "1.0"}""")
    assertEquals(parsed.sources, Nil)
    assertEquals(parsed.imports, Nil)
    assertEquals(parsed.maven, None)
  }

  test("empty maven block yields empty deps and repositories") {
    val parsed = SmithyBuildConfig.parse(
      """{"version": "1.0", "maven": {}}"""
    )
    assertEquals(parsed.maven, Some(SmithyMavenConfig(Nil, Nil)))
  }

  test("tolerates JSON comments") {
    val parsed = SmithyBuildConfig.parse(
      """{
        |  // top-level comment
        |  "version": "1.0",
        |  "sources": ["model"]
        |}""".stripMargin
    )
    assertEquals(parsed.sources, List("model"))
  }

}
