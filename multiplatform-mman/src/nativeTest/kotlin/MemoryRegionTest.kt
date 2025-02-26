/*
 * Copyright 2025 (C) Karma Krafts & associates
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

import io.karma.mman.AccessFlags
import io.karma.mman.MemoryRegion
import io.karma.mman.pageSize
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class MemoryRegionTest {
    @Test
    fun `Map and unmap private memory region`() {
        MemoryRegion.Companion.map(4096, AccessFlags.Companion.READ + AccessFlags.Companion.WRITE).use {
            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        }
    }

    @Test
    fun `Map and unmap non-existing file to shared mapping`() {
        val path = Path("newfile.txt")
        MemoryRegion.Companion.map(path, AccessFlags.Companion.READ + AccessFlags.Companion.WRITE).use {
            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        }
        SystemFileSystem.delete(path) // Delete newly created file when test is done
    }

    @Test
    fun `Map and unmap existing file to shared mapping`() {
        MemoryRegion.Companion.map(kotlinx.io.files.Path("testfile.txt"), AccessFlags.Companion.READ).use {
            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        }
    }

    @Test
    fun `Change memory protection flags of private mapping`() {
        MemoryRegion.Companion.map(pageSize shl 2, AccessFlags.Companion.READ + AccessFlags.Companion.WRITE).use {
            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(
                AccessFlags.Companion.READ + AccessFlags.Companion.WRITE, it.accessFlags, "Access flags do not match"
            )

            assertTrue(it.protect(AccessFlags.Companion.READ))

            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(AccessFlags.Companion.READ, it.accessFlags, "Access flags do not match")

            assertTrue(it.protect(AccessFlags.Companion.READ + AccessFlags.Companion.WRITE))

            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(
                AccessFlags.Companion.READ + AccessFlags.Companion.WRITE, it.accessFlags, "Access flags do not match"
            )
        }
    }

    @Test
    fun `Change memory protection flags of private file mapping`() {
        MemoryRegion.Companion.map(
            kotlinx.io.files.Path("testfile.txt"), AccessFlags.Companion.READ + AccessFlags.Companion.WRITE).use {
            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(
                AccessFlags.Companion.READ + AccessFlags.Companion.WRITE, it.accessFlags, "Access flags do not match"
            )

            assertTrue(it.protect(AccessFlags.Companion.READ))

            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(AccessFlags.Companion.READ, it.accessFlags, "Access flags do not match")

            assertTrue(it.protect(AccessFlags.Companion.READ + AccessFlags.Companion.WRITE))

            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(
                AccessFlags.Companion.READ + AccessFlags.Companion.WRITE, it.accessFlags, "Access flags do not match"
            )
        }
    }

    @Test
    fun `Resize private mapping`() {
        MemoryRegion.Companion.map(pageSize shl 2, AccessFlags.Companion.READ + AccessFlags.Companion.WRITE).use {
            assertEquals(pageSize shl 2, it.size)
            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")

            it.resize(pageSize shl 4)

            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(pageSize shl 4, it.size)

            it.resize(pageSize shl 1)

            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(pageSize shl 1, it.size)
        }
    }

    @Test
    fun `Resize shared file mapping`() {
        val path = kotlinx.io.files.Path("newfile.txt")
        MemoryRegion.Companion.map(path, AccessFlags.Companion.READ + AccessFlags.Companion.WRITE, size = pageSize shl 2).use {
            assertEquals(pageSize shl 2, it.size)
            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")

            it.resize(pageSize shl 4)

            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(pageSize shl 4, it.size)

            it.resize(pageSize shl 1)

            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(pageSize shl 1, it.size)
        }
        SystemFileSystem.delete(path) // Delete newly created file when test is done
    }

    @Test
    fun `Copy data to shared file mapping`() {
        val path = kotlinx.io.files.Path("newfile.txt")
        val sourcePath = kotlinx.io.files.Path("testfile.txt")
        MemoryRegion.Companion.map(path, AccessFlags.Companion.READ + AccessFlags.Companion.WRITE).use {
            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            it.growIfNeeded(SystemFileSystem.metadataOrNull(sourcePath)?.size ?: pageSize)
            it.asSink().apply {
                SystemFileSystem.source(sourcePath).buffered().use { source ->
                    source.transferTo(this)
                }
                flush()
            }
        }
        SystemFileSystem.source(sourcePath).buffered().use { s1 ->
            SystemFileSystem.source(path).buffered().use { s2 ->
                assertEquals(s1.readString(), s2.readString(), "File contents do not match")
            }
        }
        SystemFileSystem.delete(path) // Delete newly created file when test is done
    }

    @Test
    fun `Copy data from shared file mapping`() {
        val path = kotlinx.io.files.Path("testfile.txt")
        val destPath = kotlinx.io.files.Path("newfile.txt")
        MemoryRegion.Companion.map(path, AccessFlags.Companion.READ).use {
            assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
            SystemFileSystem.sink(destPath).buffered().use { sink ->
                sink.transferFrom(it.asSource())
                sink.flush()
            }
        }
        SystemFileSystem.source(path).buffered().use { s1 ->
            SystemFileSystem.source(destPath).buffered().use { s2 ->
                assertEquals(s1.readString(), s2.readString(), "File contents do not match")
            }
        }
        SystemFileSystem.delete(destPath) // Delete newly created file when test is done
    }
}