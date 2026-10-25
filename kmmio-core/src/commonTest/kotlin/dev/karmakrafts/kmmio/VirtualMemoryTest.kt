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
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VirtualMemoryTest {
    @Test
    fun mapAndUnmapAnonMemory() {
        val memory = VirtualMemory(1024 * 64)
        memory.close()
    }

    @Test
    fun mapAndUnmapSharedMemory() {
        val path = Path(SystemTemporaryDirectory, "kmmio-${Random.nextLong()}.bin")
        val data = "Hello, shared memory!".encodeToByteArray()
        assertFalse(SystemFileSystem.exists(path))
        try {
            VirtualMemory(1024 * 64, path).use { memory ->
                assertTrue(memory.isFileBacked)
                assertEquals(MappingFlags.SHARED, memory.mappingFlags)
                assertEquals(data.size.toLong(), memory.writeBytes(data))
                assertTrue(memory.sync(SyncFlags.SYNC))
            }
            SystemFileSystem.source(path).buffered().use { source ->
                assertContentEquals(data, source.readByteArray(data.size))
            }
            VirtualMemory(1024 * 64, path).use { memory ->
                val actual = ByteArray(data.size)
                assertEquals(data.size.toLong(), memory.readBytes(actual, actual.size.toLong()))
                assertContentEquals(data, actual)
            }
        }
        finally {
            SystemFileSystem.delete(path, mustExist = false)
        }
    }

    @Test
    fun mapFileInMissingDirectory() {
        val directory = Path(SystemTemporaryDirectory, "kmmio-missing-${Random.nextLong()}")
        assertFalse(SystemFileSystem.exists(directory))
        assertFailsWith<IllegalStateException> {
            VirtualMemory(1024 * 64, Path(directory, "testfile.bin"))
        }
    }
}