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

import software.amazon.smithy.model.loader.IdlTokenizer
import software.amazon.smithy.syntax.Formatter
import software.amazon.smithy.syntax.TokenTree

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object SmithyFormat {

  def format(fileName: String, source: String): String = {
    val tokenizer = IdlTokenizer.create(fileName, source)
    val tree = TokenTree.of(tokenizer)
    Formatter.format(tree)
  }

  def smithyFiles(roots: Seq[Path]): Seq[Path] = {
    val seen = scala.collection.mutable.LinkedHashSet.empty[Path]
    roots.filter(Files.exists(_)).foreach { root =>
      if (Files.isRegularFile(root)) {
        seen += root.toAbsolutePath.normalize()
      } else {
        val stream = Files.walk(root)
        try {
          val it = stream.iterator()
          while (it.hasNext) {
            val p = it.next()
            if (Files.isRegularFile(p) && p.getFileName.toString.endsWith(".smithy"))
              seen += p.toAbsolutePath.normalize()
          }
        } finally stream.close()
      }
    }
    seen.toSeq
  }

  /** Format each file in place. Returns the files that were rewritten. */
  def formatAll(roots: Seq[Path]): Seq[Path] =
    smithyFiles(roots).flatMap { file =>
      val original = readUtf8(file)
      val formatted = format(file.toString, original)
      if (formatted == original) None
      else {
        writeUtf8(file, formatted)
        Some(file)
      }
    }

  /** Returns files that are not properly formatted. */
  def checkAll(roots: Seq[Path]): Seq[Path] =
    smithyFiles(roots).filter { file =>
      val original = readUtf8(file)
      format(file.toString, original) != original
    }

  private def readUtf8(p: Path): String =
    new String(Files.readAllBytes(p), StandardCharsets.UTF_8)

  private def writeUtf8(p: Path, content: String): Unit = {
    Files.write(p, content.getBytes(StandardCharsets.UTF_8))
    ()
  }

}
