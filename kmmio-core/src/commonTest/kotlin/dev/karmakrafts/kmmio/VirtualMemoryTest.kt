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

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VirtualMemoryTest {
    @Test
    fun mapAndUnmapAnonMemory() {
        VirtualMemory(1024 * 64).use { memory ->
            assertEquals(1024L * 64, memory.size)
            assertNotEquals(0L, memory.address)
            assertNotEquals(-1L, memory.address)
            assertNull(memory.path)
            assertFalse(memory.isFileBacked)
            assertEquals(-1, VirtualMemory.INVALID_FILE_DESCRIPTOR)
            assertEquals(VirtualMemory.INVALID_FILE_DESCRIPTOR, memory.fileDescriptor)
            assertEquals(AccessFlags.READ + AccessFlags.WRITE, memory.accessFlags)
            assertEquals(MappingFlags.ANON + MappingFlags.PRIVATE, memory.mappingFlags)
        }
    }

    @Test
    fun mapAndUnmapSharedMemory() {
        val data = "Hello, shared memory!".encodeToByteArray()
        withTemporaryFile { path ->
            VirtualMemory(1024 * 64, path).use { memory ->
                assertEquals(path, memory.path)
                assertNotEquals(VirtualMemory.INVALID_FILE_DESCRIPTOR, memory.fileDescriptor)
                assertTrue(memory.isFileBacked)
                assertEquals(MappingFlags.SHARED, memory.mappingFlags)
                assertEquals(data.size.toLong(), memory.writeBytes(data))
                assertTrue(memory.sync(SyncFlags.ASYNC))
                assertTrue(memory.sync(SyncFlags.SYNC))
            }
            assertEquals(1024 * 64, readFileBytes(path).size)
            assertContentEquals(data, readFileBytes(path).copyOf(data.size))
            VirtualMemory(1024 * 64, path).use { memory ->
                val actual = ByteArray(data.size)
                assertEquals(data.size.toLong(), memory.readBytes(actual, actual.size.toLong()))
                assertContentEquals(data, actual)
            }
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

    @Test
    fun explicitFlagsAreRetained() {
        val mappingFlags = MappingFlags.PRIVATE + MappingFlags.ANON
        VirtualMemory(16, accessFlags = AccessFlags.READ, mappingFlags = mappingFlags).use { memory ->
            assertEquals(AccessFlags.READ, memory.accessFlags)
            assertEquals(mappingFlags, memory.mappingFlags)
            assertContentEquals(ByteArray(16), memory.readAllBytes())
        }
    }

    @Test
    fun anonymousSharedMappingWhenAvailable() {
        if (!VirtualMemory.isSharedAvailable) return
        VirtualMemory(16, mappingFlags = MappingFlags.ANON + MappingFlags.SHARED).use { memory ->
            assertFalse(memory.isFileBacked)
            assertEquals(MappingFlags.ANON + MappingFlags.SHARED, memory.mappingFlags)
            assertEquals(3L, memory.writeBytes(byteArrayOf(1, 2, 3)))
            assertContentEquals(byteArrayOf(1, 2, 3), memory.readAllBytes().copyOf(3))
        }
    }

    @Test
    fun readAllBytesReturnsAnIndependentSnapshot() {
        val data = ByteArray(32) { (it * 37).toByte() }
        VirtualMemory(32).use { memory ->
            assertEquals(32L, memory.writeBytes(data))
            val snapshot = memory.readAllBytes()
            assertContentEquals(data, snapshot)
            snapshot.fill(42)
            assertContentEquals(data, memory.readAllBytes())
            memory.zero()
            assertContentEquals(ByteArray(32), memory.readAllBytes())
            assertContentEquals(ByteArray(32) { 42 }, snapshot)
        }
    }

    @Test
    fun readAllBytesRejectsSizesAboveTheArrayLimit() {
        VirtualMemory(1).use { memory ->
            val oversized = object : VirtualMemory by memory {
                override val size: Long = Int.MAX_VALUE.toLong() + 1
                override fun readAllBytes(): ByteArray = super.readAllBytes()
            }
            assertFailsWith<IllegalStateException> { oversized.readAllBytes() }
        }
    }

    @Test
    fun readsWithBothOffsetsAndPreservesSurroundingBytes() {
        val data = ByteArray(16) { it.toByte() }
        VirtualMemory(16).use { memory ->
            memory.writeBytes(data)
            val actual = ByteArray(10) { 42 }
            assertEquals(4L, memory.readBytes(actual, size = 4, srcOffset = 3, dstOffset = 2))
            assertContentEquals(byteArrayOf(42, 42, 3, 4, 5, 6, 42, 42, 42, 42), actual)
        }
    }

    @Test
    fun writesWithBothOffsetsAndPreservesSurroundingBytes() {
        VirtualMemory(12).use { memory ->
            memory.zero()
            assertEquals(3L, memory.writeBytes(byteArrayOf(9, 1, 2, 3, 9), 3, 1, 4))
            val expected = ByteArray(12)
            byteArrayOf(1, 2, 3).copyInto(expected, 4)
            assertContentEquals(expected, memory.readAllBytes())
        }
    }

    @Test
    fun transfersAreClampedToTheMemoryAndArrayBounds() {
        VirtualMemory(4).use { memory ->
            assertEquals(4L, memory.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6)))
            val small = ByteArray(2)
            assertEquals(2L, memory.readBytes(small))
            assertContentEquals(byteArrayOf(1, 2), small)
            val large = ByteArray(8) { 42 }
            assertEquals(2L, memory.readBytes(large, 20, 2, 1))
            assertContentEquals(byteArrayOf(42, 3, 4, 42, 42, 42, 42, 42), large)
            assertEquals(1L, memory.writeBytes(byteArrayOf(8, 9), 20, 1, 3))
            assertContentEquals(byteArrayOf(1, 2, 3, 9), memory.readAllBytes())
        }
    }

    @Test
    fun zeroLengthTransfersAtTheEndDoNotChangeMemory() {
        VirtualMemory(4).use { memory ->
            val data = byteArrayOf(1, 2, 3, 4)
            memory.writeBytes(data)
            assertEquals(0L, memory.readBytes(ByteArray(0), 0, 4, 0))
            assertEquals(0L, memory.writeBytes(ByteArray(0), 0, 0, 4))
            assertContentEquals(data, memory.readAllBytes())
        }
    }

    @Test
    fun copyToUsesOffsetsAndByteCounts() {
        VirtualMemory(16).use { source ->
            VirtualMemory(12).use { destination ->
                source.writeBytes(ByteArray(16) { it.toByte() })
                destination.zero()
                assertEquals(4L, source.copyTo(destination, 4, 3, 5))
                val expected = ByteArray(12)
                byteArrayOf(3, 4, 5, 6).copyInto(expected, 5)
                assertContentEquals(expected, destination.readAllBytes())
            }
        }
    }

    @Test
    fun copyFromUsesOffsetsAndByteCounts() {
        VirtualMemory(16).use { source ->
            VirtualMemory(12).use { destination ->
                source.writeBytes(ByteArray(16) { it.toByte() })
                destination.zero()
                assertEquals(4L, destination.copyFrom(source, 4, 3, 5))
                val expected = ByteArray(12)
                byteArrayOf(3, 4, 5, 6).copyInto(expected, 5)
                assertContentEquals(expected, destination.readAllBytes())
            }
        }
    }

    @Test
    fun copiesAreClampedToBothRegions() {
        VirtualMemory(8).use { source ->
            VirtualMemory(4).use { destination ->
                source.writeBytes(ByteArray(8) { (it + 1).toByte() })
                destination.zero()
                assertEquals(2L, source.copyTo(destination, 20, 1, 2))
                assertContentEquals(byteArrayOf(0, 0, 2, 3), destination.readAllBytes())
                assertEquals(1L, source.copyTo(destination, 20, 7, 0))
                assertEquals(1L, destination.copyFrom(source, 20, 7, 1))
                assertContentEquals(byteArrayOf(8, 8, 2, 3), destination.readAllBytes())
                assertEquals(4L, destination.copyFrom(source))
                assertContentEquals(byteArrayOf(1, 2, 3, 4), destination.readAllBytes())
            }
        }
    }

    @Test
    fun zeroLengthCopiesAtTheEndDoNotChangeMemory() {
        VirtualMemory(4).use { source ->
            VirtualMemory(4).use { destination ->
                destination.zero()
                assertEquals(0L, source.copyTo(destination, 0, 4, 4))
                assertEquals(0L, destination.copyFrom(source, 0, 4, 4))
                assertContentEquals(ByteArray(4), destination.readAllBytes())
            }
        }
    }

    @Test
    fun resizeUpdatesSizeAndAllowsAccessToTheNewRegion() {
        VirtualMemory(16).use { memory ->
            val address = memory.address
            assertFalse(memory.resize(16))
            assertEquals(address, memory.address)
            assertTrue(memory.resize(32))
            assertEquals(32L, memory.size)
            memory.zero()
            assertEquals(1L, memory.writeBytes(byteArrayOf(42), dstOffset = 31))
            assertEquals(42.toByte(), memory.readAllBytes()[31])
            assertTrue(memory.resize(8))
            assertEquals(8L, memory.size)
            memory.zero()
            assertContentEquals(ByteArray(8), memory.readAllBytes())
        }
    }

    @Test
    fun fileResizePreservesDataAndUpdatesTheFileLength() {
        withTemporaryFile { path ->
            VirtualMemory(16, path).use { memory ->
                val data = byteArrayOf(1, 2, 3, 4)
                memory.writeBytes(data)
                assertTrue(memory.sync(SyncFlags.SYNC))
                assertTrue(memory.resize(32))
                assertEquals(32L, memory.size)
                assertContentEquals(data, memory.readAllBytes().copyOf(data.size))
                assertEquals(32, readFileBytes(path).size)
                assertTrue(memory.resize(8))
                assertEquals(8L, memory.size)
                assertContentEquals(data, memory.readAllBytes().copyOf(data.size))
            }
            assertEquals(8, readFileBytes(path).size)
        }
    }

    @Test
    fun protectUpdatesTheReportedAccessFlags() {
        VirtualMemory(16).use { memory ->
            val data = byteArrayOf(1, 2, 3, 4)
            memory.writeBytes(data)
            assertTrue(memory.protect(AccessFlags.READ))
            try {
                assertEquals(AccessFlags.READ, memory.accessFlags)
                assertContentEquals(data, memory.readAllBytes().copyOf(data.size))
            }
            finally {
                assertTrue(memory.protect(AccessFlags.READ + AccessFlags.WRITE))
            }
            assertEquals(AccessFlags.READ + AccessFlags.WRITE, memory.accessFlags)
            assertEquals(1L, memory.writeBytes(byteArrayOf(42)))
        }
    }

    @Test
    fun lockedMemoryCanBeUnlocked() {
        VirtualMemory(16).use { memory ->
            // Locking may be denied by the operating system's locked-memory quota.
            if (memory.lock()) assertTrue(memory.unlock())
            memory.zero()
            assertContentEquals(ByteArray(16), memory.readAllBytes())
        }
    }

    @Test
    fun platformViewSharesTheMemory() {
        VirtualMemory(16).use(::assertPlatformMemoryView)
    }
}