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

import io.karma.kmmio.AccessFlags
import io.karma.kmmio.MemoryRegion
import io.karma.kmmio.pageSize
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
        MemoryRegion.map(4096, AccessFlags.READ + AccessFlags.WRITE).use { region ->
            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
        }
    }

    @Test
    fun `Map and unmap non-existing file to shared mapping`() {
        val path = Path("newfile.txt")
        MemoryRegion.map(path, AccessFlags.READ + AccessFlags.WRITE).use { region ->
            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
        }
        SystemFileSystem.delete(path) // Delete newly created file when test is done
    }

    @Test
    fun `Map and unmap existing file to shared mapping`() {
        MemoryRegion.map(Path("testfile.txt"), AccessFlags.READ).use { region ->
            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
        }
    }

    @Test
    fun `Change memory protection flags of private mapping`() {
        MemoryRegion.map(pageSize shl 2, AccessFlags.READ + AccessFlags.WRITE).use { region ->
            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(
                AccessFlags.READ + AccessFlags.WRITE, region.accessFlags, "Access flags do not match"
            )

            assertTrue(region.protect(AccessFlags.READ))

            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(AccessFlags.READ, region.accessFlags, "Access flags do not match")

            assertTrue(region.protect(AccessFlags.READ + AccessFlags.WRITE))

            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(
                AccessFlags.READ + AccessFlags.WRITE, region.accessFlags, "Access flags do not match"
            )
        }
    }

    @Test
    fun `Change memory protection flags of private file mapping`() {
        MemoryRegion.map(
            Path("testfile.txt"), AccessFlags.READ + AccessFlags.WRITE
        ).use { region ->
            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(
                AccessFlags.READ + AccessFlags.WRITE, region.accessFlags, "Access flags do not match"
            )

            assertTrue(region.protect(AccessFlags.READ))

            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(AccessFlags.READ, region.accessFlags, "Access flags do not match")

            assertTrue(region.protect(AccessFlags.READ + AccessFlags.WRITE))

            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(
                AccessFlags.READ + AccessFlags.WRITE, region.accessFlags, "Access flags do not match"
            )
        }
    }

    @Test
    fun `Resize private mapping`() {
        MemoryRegion.map(pageSize shl 2, AccessFlags.READ + AccessFlags.WRITE).use { region ->
            assertEquals(pageSize shl 2, region.size)
            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")

            region.resize(pageSize shl 4)

            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(pageSize shl 4, region.size)

            region.resize(pageSize shl 1)

            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(pageSize shl 1, region.size)
        }
    }

    @Test
    fun `Resize shared file mapping`() {
        val path = Path("newfile.txt")
        MemoryRegion.map(path, AccessFlags.READ + AccessFlags.WRITE, size = pageSize shl 2).use { region ->
            assertEquals(pageSize shl 2, region.size)
            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")

            region.resize(pageSize shl 4)

            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(pageSize shl 4, region.size)

            region.resize(pageSize shl 1)

            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            assertEquals(pageSize shl 1, region.size)
        }
        SystemFileSystem.delete(path) // Delete newly created file when test is done
    }

    @Test
    fun `Copy data to shared file mapping`() {
        val destinationPath = Path("newfile.txt")
        val sourcePath = Path("testfile.txt")
        MemoryRegion.map(destinationPath, AccessFlags.READ + AccessFlags.WRITE).use { region ->
            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            region.growIfNeeded(SystemFileSystem.metadataOrNull(sourcePath)?.size ?: pageSize)
            region.asSink().apply {
                SystemFileSystem.source(sourcePath).buffered().use { source ->
                    source.transferTo(this)
                }
                flush()
            }
        }
        SystemFileSystem.source(sourcePath).buffered().use { expectedSource ->
            SystemFileSystem.source(destinationPath).buffered().use { copiedSource ->
                assertEquals(expectedSource.readString(), copiedSource.readString(), "File contents do not match")
            }
        }
        SystemFileSystem.delete(destinationPath) // Delete newly created file when test is done
    }

    @Test
    fun `Copy data from shared file mapping`() {
        val sourcePath = Path("testfile.txt")
        val destinationPath = Path("newfile.txt")
        MemoryRegion.map(sourcePath, AccessFlags.READ).use { region ->
            assertNotEquals(0, region.address.rawValue.toLong(), "Address cannot be 0")
            SystemFileSystem.sink(destinationPath).buffered().use { sink ->
                sink.transferFrom(region.asSource())
                sink.flush()
            }
        }
        SystemFileSystem.source(sourcePath).buffered().use { expectedSource ->
            SystemFileSystem.source(destinationPath).buffered().use { copiedSource ->
                assertEquals(expectedSource.readString(), copiedSource.readString(), "File contents do not match")
            }
        }
        SystemFileSystem.delete(destinationPath) // Delete newly created file when test is done
    }
}