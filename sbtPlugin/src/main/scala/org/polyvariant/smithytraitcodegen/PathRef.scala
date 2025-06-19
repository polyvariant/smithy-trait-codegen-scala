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

import sbt.io.Hash
import sbt.util.FileInfo
import sbt.util.HashFileInfo
import sjsonnew.*

import java.io.File

case class PathRef(path: os.Path)

object PathRef {

  def apply(f: File): PathRef = PathRef(os.Path(f))

  implicit val pathFormat: JsonFormat[PathRef] = BasicJsonProtocol
    .projectFormat[PathRef, HashFileInfo](
      p =>
        if (os.isFile(p.path))
          FileInfo.hash(p.path.toIO)
        else
          // If the path is a directory, we get the hashes of all files
          // then hash the concatenation of the hash's bytes.
          FileInfo.hash(
            p.path.toIO,
            Hash(
              os.walk(p.path)
                .map(_.toIO)
                .map(Hash(_))
                .foldLeft(Array.emptyByteArray)(_ ++ _)
            ),
          ),
      hash => PathRef(hash.file),
    )

}
