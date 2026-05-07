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
import sjsonnew.support.scalajson.unsafe.Converter
import sjsonnew.support.scalajson.unsafe.CompactPrinter

class PathRefTest extends FunSuite {

  private val sandbox = FunFixture[os.Path](
    setup = _ => os.temp.dir(prefix = "pathref-test-"),
    teardown = dir => os.remove.all(dir),
  )

  // The PathRef JsonFormat serializes via HashFileInfo, which embeds the content hash
  // — so the rendered JSON changes iff the hash changes.
  private def hashJson(p: os.Path): String = CompactPrinter(Converter.toJsonUnsafe(PathRef(p)))

  sandbox.test("hashes a single file by content") { dir =>
    val file = dir / "a.txt"
    os.write(file, "hello")

    val before = hashJson(file)
    os.write.over(file, "world")
    val after = hashJson(file)

    assertNotEquals(before, after)
  }

  sandbox.test("directory hash changes when a contained file changes") { dir =>
    os.write(dir / "a.txt", "one", createFolders = true)
    os.write(dir / "nested" / "b.txt", "two", createFolders = true)

    val before = hashJson(dir)
    os.write.over(dir / "nested" / "b.txt", "changed")
    val after = hashJson(dir)

    assertNotEquals(before, after)
  }

  sandbox.test("directory hash is stable when contents are unchanged") { dir =>
    os.write(dir / "a.txt", "one", createFolders = true)
    os.write(dir / "nested" / "b.txt", "two", createFolders = true)

    assertEquals(hashJson(dir), hashJson(dir))
  }

  sandbox.test("directory hash changes when a new file is added") { dir =>
    os.write(dir / "a.txt", "one", createFolders = true)
    val before = hashJson(dir)

    os.write(dir / "b.txt", "two")
    val after = hashJson(dir)

    assertNotEquals(before, after)
  }

}
