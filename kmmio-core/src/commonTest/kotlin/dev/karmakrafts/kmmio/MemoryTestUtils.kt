/*
 * Copyright 2026 Karma Krafts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.karmakrafts.kmmio

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlin.random.Random
import kotlin.test.assertFalse

internal fun withTemporaryFile(block: (Path) -> Unit) {
    val path = Path(SystemTemporaryDirectory, "kmmio-${Random.nextLong()}.bin")
    assertFalse(SystemFileSystem.exists(path))
    try {
        block(path)
    }
    finally {
        SystemFileSystem.delete(path, mustExist = false)
    }
}

internal fun readFileBytes(path: Path): ByteArray = SystemFileSystem.source(path).buffered().use { it.readByteArray() }

internal expect fun assertPlatformMemoryView(memory: VirtualMemory)