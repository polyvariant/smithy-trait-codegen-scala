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

import munit.FunSuite

import java.nio.file.Path

class SmithyFormatTest extends FunSuite {

  private val sandbox = FunFixture[os.Path](
    setup = _ => os.temp.dir(prefix = "smithyformat-test-"),
    teardown = dir => os.remove.all(dir),
  )

  private val unformatted =
    """$version: "2"
      |
      |namespace demo
      |
      |structure   Hello {
      |    name :   String
      |}
      |""".stripMargin

  private val formatted =
    """$version: "2"
      |
      |namespace demo
      |
      |structure Hello {
      |    name: String
      |}
      |""".stripMargin

  test("format normalizes spacing") {
    assertEquals(SmithyFormat.format("test.smithy", unformatted), formatted)
  }

  test("format on already-formatted input is a no-op") {
    assertEquals(SmithyFormat.format("test.smithy", formatted), formatted)
  }

  sandbox.test("smithyFiles walks directories and picks .smithy files") { dir =>
    os.write(dir / "a.smithy", unformatted, createFolders = true)
    os.write(dir / "nested" / "b.smithy", formatted, createFolders = true)
    os.write(dir / "ignored.txt", "nope", createFolders = true)
    os.write(dir / "nested" / "ignored.json", "nope", createFolders = true)

    val found = SmithyFormat.smithyFiles(Seq(toPath(dir))).map(_.getFileName.toString).toSet
    assertEquals(found, Set("a.smithy", "b.smithy"))
  }

  sandbox.test("smithyFiles accepts a single file as a root") { dir =>
    val file = dir / "lone.smithy"
    os.write(file, unformatted)
    val found = SmithyFormat.smithyFiles(Seq(toPath(file))).map(_.getFileName.toString)
    assertEquals(found, Seq("lone.smithy"))
  }

  sandbox.test("smithyFiles ignores non-existent roots") { dir =>
    val found = SmithyFormat.smithyFiles(Seq(toPath(dir / "missing")))
    assertEquals(found, Seq.empty[Path])
  }

  sandbox.test("smithyFiles dedupes overlapping roots") { dir =>
    val nested = dir / "nested"
    os.write(nested / "a.smithy", unformatted, createFolders = true)
    val found = SmithyFormat.smithyFiles(Seq(toPath(dir), toPath(nested)))
    assertEquals(found.size, 1)
  }

  sandbox.test("smithyFiles discovers files from src/main/smithy and META-INF roots") { dir =>
    // mirrors the plugin's default Compile source roots
    val smithyRoot = dir / "src" / "main" / "smithy"
    val metaInfRoot = dir / "src" / "main" / "resources" / "META-INF" / "smithy"
    os.write(smithyRoot / "fromSmithy.smithy", unformatted, createFolders = true)
    os.write(metaInfRoot / "fromMetaInf.smithy", formatted, createFolders = true)

    val found =
      SmithyFormat
        .smithyFiles(Seq(toPath(smithyRoot), toPath(metaInfRoot)))
        .map(_.getFileName.toString)
        .toSet
    assertEquals(found, Set("fromSmithy.smithy", "fromMetaInf.smithy"))
  }

  sandbox.test("formatAll rewrites only files that need it; returns rewritten paths") { dir =>
    val bad = dir / "bad.smithy"
    val good = dir / "good.smithy"
    os.write(bad, unformatted)
    os.write(good, formatted)

    val rewritten = SmithyFormat.formatAll(Seq(toPath(dir)))
    assertEquals(rewritten.map(_.getFileName.toString).toSet, Set("bad.smithy"))
    assertEquals(os.read(bad), formatted)
    assertEquals(os.read(good), formatted)

    // running again is a no-op
    assertEquals(SmithyFormat.formatAll(Seq(toPath(dir))), Seq.empty[Path])
  }

  sandbox.test("checkAll returns only files that would be reformatted") { dir =>
    val bad = dir / "bad.smithy"
    val good = dir / "good.smithy"
    os.write(bad, unformatted)
    os.write(good, formatted)

    val unformattedFiles = SmithyFormat.checkAll(Seq(toPath(dir)))
    assertEquals(unformattedFiles.map(_.getFileName.toString).toSet, Set("bad.smithy"))

    // checkAll does not mutate the file system
    assertEquals(os.read(bad), unformatted)
  }

  private def toPath(p: os.Path): Path = p.toNIO

}
