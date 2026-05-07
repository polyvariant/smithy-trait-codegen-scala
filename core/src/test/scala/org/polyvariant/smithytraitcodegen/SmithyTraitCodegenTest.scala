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

import munit.FunSuite

class SmithyTraitCodegenTest extends FunSuite {

  private val sandbox = FunFixture[os.Path](
    setup = _ => os.temp.dir(prefix = "smithy-trait-codegen-test-"),
    teardown = dir => os.remove.all(dir),
  )

  private def writeSmithy(dir: os.Path, name: String, content: String): os.Path = {
    val file = dir / name
    os.write.over(file, content, createFolders = true)
    file
  }

  private def runCodegen(
    workDir: os.Path,
    smithy: String,
    javaPackage: String = "demo",
    smithyNamespace: String = "demo",
    externalProviders: List[String] = Nil,
  ): SmithyTraitCodegen.Output = {
    val sources = workDir / "sources"
    writeSmithy(sources, "demo.smithy", smithy)
    SmithyTraitCodegen.generate(
      SmithyTraitCodegen.Args(
        javaPackage = javaPackage,
        smithyNamespace = smithyNamespace,
        targetDir = workDir / "target",
        smithySourcesDir = sources,
        dependencies = Nil,
        externalProviders = externalProviders,
      )
    )
  }

  private def traitServiceFile(out: SmithyTraitCodegen.Output): os.Path =
    out.metaDir / "META-INF" / "services" / "software.amazon.smithy.model.traits.TraitService"

  sandbox.test("generate emits a Java class and a TraitService entry for a trait") { dir =>
    val out = runCodegen(
      dir,
      """$version: "2"
        |
        |namespace demo
        |
        |@trait
        |structure myTrait {
        |    @required
        |    name: String
        |}
        |""".stripMargin,
    )

    val javaFile = out.javaDir / "demo" / "MyTrait.java"
    assert(os.exists(javaFile), s"expected $javaFile to exist")

    val providers = os.read.lines(traitServiceFile(out)).filter(_.nonEmpty)
    assertEquals(providers, IndexedSeq("demo.MyTrait$Provider"))
  }

  sandbox.test("generate skips traits tagged with nocodegen") { dir =>
    val out = runCodegen(
      dir,
      """$version: "2"
        |
        |namespace demo
        |
        |@trait
        |@tags(["nocodegen"])
        |structure skippedTrait {
        |    @required
        |    name: String
        |}
        |
        |@trait
        |structure keptTrait {
        |    @required
        |    name: String
        |}
        |""".stripMargin,
    )

    assert(os.exists(out.javaDir / "demo" / "KeptTrait.java"), "kept trait should be generated")
    assert(
      !os.exists(out.javaDir / "demo" / "SkippedTrait.java"),
      "nocodegen-tagged trait should be skipped",
    )

    val providers = os.read.lines(traitServiceFile(out)).filter(_.nonEmpty)
    assertEquals(providers, IndexedSeq("demo.KeptTrait$Provider"))
  }

  sandbox.test("externalProviders are appended to the TraitService file") { dir =>
    val out = runCodegen(
      dir,
      """$version: "2"
        |
        |namespace demo
        |
        |@trait
        |structure myTrait {
        |    @required
        |    name: String
        |}
        |""".stripMargin,
      externalProviders = List("com.example.ExternalProvider", "com.example.AnotherProvider"),
    )

    val providers = os.read.lines(traitServiceFile(out)).filter(_.nonEmpty)
    assertEquals(
      providers,
      IndexedSeq(
        "demo.MyTrait$Provider",
        "com.example.ExternalProvider",
        "com.example.AnotherProvider",
      ),
    )
  }

  sandbox.test("generate cleans the output dir on rerun") { dir =>
    val firstSmithy =
      """$version: "2"
        |
        |namespace demo
        |
        |@trait
        |structure firstTrait {
        |    @required
        |    name: String
        |}
        |""".stripMargin

    val secondSmithy =
      """$version: "2"
        |
        |namespace demo
        |
        |@trait
        |structure secondTrait {
        |    @required
        |    name: String
        |}
        |""".stripMargin

    val firstOut = runCodegen(dir, firstSmithy)
    assert(os.exists(firstOut.javaDir / "demo" / "FirstTrait.java"))

    val secondOut = runCodegen(dir, secondSmithy)
    assert(!os.exists(secondOut.javaDir / "demo" / "FirstTrait.java"))
    assert(os.exists(secondOut.javaDir / "demo" / "SecondTrait.java"))
  }

}
