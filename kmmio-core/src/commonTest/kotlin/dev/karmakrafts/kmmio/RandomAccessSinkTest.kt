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

import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RandomAccessSinkTest {
    @Test
    fun writeToVirtualMemorySink() {
        val data = "Hello, sink!".encodeToByteArray()
        VirtualMemory(data.size.toLong()).use { memory ->
            memory.sink().use { sink ->
                val buffer = Buffer().apply { write(data) }
                sink.write(buffer, data.size.toLong())
                sink.flush()
                assertEquals(0L, buffer.size)
                assertEquals(data.size.toLong(), sink.tell())
                assertContentEquals(data, memory.readAllBytes())
            }
        }
    }

    @Test
    fun writesMultipleChunksAndLeavesUnrequestedBytesInBuffer() {
        val data = ByteArray(8192 * 2 + 17) { (it * 37).toByte() }
        VirtualMemory(data.size.toLong()).use { memory ->
            memory.sink().use { sink ->
                val buffer = Buffer().apply { write(data + byteArrayOf(42)) }
                sink.write(buffer, 7)
                sink.write(buffer, (data.size - 7).toLong())
                assertEquals(data.size.toLong(), sink.tell())
                assertContentEquals(data, memory.readAllBytes())
                assertContentEquals(byteArrayOf(42), buffer.readByteArray())
            }
        }
    }

    @Test
    fun writesOnlyTheSelectedRegionAndOverwritesAfterSeeking() {
        VirtualMemory(16).use { memory ->
            memory.zero()
            memory.sink(size = 4, offset = 5).use { sink ->
                sink.write(Buffer().apply { write(byteArrayOf(1, 2, 3, 4)) }, 4)
                sink.seek(-2, RandomAccess.Whence.END)
                sink.write(Buffer().apply { write(byteArrayOf(9)) }, 1)
                assertEquals(3L, sink.tell())
                val expected = ByteArray(16)
                byteArrayOf(1, 2, 9, 4).copyInto(expected, 5)
                assertContentEquals(expected, memory.readAllBytes())
            }
        }
    }

    @Test
    fun zeroByteWritesDoNotChangePositionOrBuffer() {
        VirtualMemory(8).use { memory ->
            memory.zero()
            memory.sink().use { sink ->
                val buffer = Buffer().apply { write(byteArrayOf(42)) }
                sink.write(buffer, 0)
                assertEquals(0L, sink.tell())
                sink.seek(0, RandomAccess.Whence.END)
                sink.write(buffer, 0)
                assertEquals(8L, sink.tell())
                assertContentEquals(byteArrayOf(42), buffer.readByteArray())
                assertContentEquals(ByteArray(8), memory.readAllBytes())
            }
        }
    }

    @Test
    fun rejectsWritesLargerThanTheRegionWithoutConsumingData() {
        VirtualMemory(8).use { memory ->
            memory.zero()
            memory.sink(size = 2, offset = 3).use { sink ->
                val data = byteArrayOf(1, 2, 3)
                val buffer = Buffer().apply { write(data) }
                assertFailsWith<IllegalArgumentException> { sink.write(buffer, 3) }
                assertEquals(0L, sink.tell())
                assertContentEquals(data, buffer.readByteArray())
                assertContentEquals(ByteArray(8), memory.readAllBytes())
            }
        }
    }

    @Test
    fun rejectsNegativeByteCount() {
        VirtualMemory(8).use { memory ->
            memory.sink().use { sink ->
                val buffer = Buffer().apply { write(byteArrayOf(42)) }
                assertFailsWith<IllegalArgumentException> { sink.write(buffer, -1) }
                assertEquals(0L, sink.tell())
                assertContentEquals(byteArrayOf(42), buffer.readByteArray())
            }
        }
    }

    @Test
    fun reportsInsufficientSourceData() {
        VirtualMemory(8).use { memory ->
            memory.sink().use { sink ->
                val buffer = Buffer().apply { write(byteArrayOf(1, 2)) }
                assertFailsWith<IllegalStateException> { sink.write(buffer, 3) }
                assertEquals(2L, sink.tell())
                assertEquals(0L, buffer.size)
                val actual = ByteArray(2)
                assertEquals(2L, memory.readBytes(actual, 2))
                assertContentEquals(byteArrayOf(1, 2), actual)
            }
        }
    }

    @Test
    fun emptyRegionRejectsNonEmptyWrites() {
        VirtualMemory(8).use { memory ->
            memory.sink(size = 0, offset = 8).use { sink ->
                val buffer = Buffer().apply { write(byteArrayOf(42)) }
                sink.write(buffer, 0)
                assertFailsWith<IllegalArgumentException> { sink.write(buffer, 1) }
                assertEquals(0L, sink.tell())
                assertEquals(1L, buffer.size)
            }
        }
    }

    @Test
    fun flushPersistsFileBackedWrites() {
        withTemporaryFile { path ->
            val data = byteArrayOf(1, 2, 3, 4)
            VirtualMemory(16, path).use { memory ->
                memory.sink().use { sink ->
                    sink.write(Buffer().apply { write(data) }, data.size.toLong())
                    sink.flush()
                    assertContentEquals(data, readFileBytes(path).copyOf(data.size))
                }
            }
        }
    }

    @Test
    fun closeDoesNotOwnTheMemory() {
        VirtualMemory(8).use { memory ->
            val sink = memory.sink()
            sink.close()
            memory.writeBytes(byteArrayOf(42))
            assertEquals(42.toByte(), memory.readAllBytes()[0])
        }
    }
}
