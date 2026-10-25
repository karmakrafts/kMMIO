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

class RandomAccessSourceTest {
    @Test
    fun readFromVirtualMemorySource() {
        val data = "Hello, source!".encodeToByteArray()
        VirtualMemory(data.size.toLong()).use { memory ->
            memory.writeBytes(data)
            memory.source().use { source ->
                val buffer = Buffer()
                assertEquals(data.size.toLong(), source.readAtMostTo(buffer, data.size.toLong()))
                assertEquals(data.size.toLong(), source.tell())
                assertContentEquals(data, buffer.readByteArray())
                assertEquals(-1L, source.readAtMostTo(buffer, 1))
            }
        }
    }

    @Test
    fun readsMultipleChunksAndAppendsToTheBuffer() {
        val data = ByteArray(8192 * 2 + 17) { (it * 37).toByte() }
        VirtualMemory(data.size.toLong()).use { memory ->
            memory.writeBytes(data)
            memory.source().use { source ->
                val buffer = Buffer().apply { write(byteArrayOf(42)) }
                assertEquals(7L, source.readAtMostTo(buffer, 7))
                assertEquals((data.size - 7).toLong(), source.readAtMostTo(buffer, Long.MAX_VALUE))
                assertContentEquals(byteArrayOf(42) + data, buffer.readByteArray())
                assertEquals(data.size.toLong(), source.tell())
                assertEquals(-1L, source.readAtMostTo(buffer, 1))
            }
        }
    }

    @Test
    fun readsOnlyTheSelectedRegionAndCanRereadAfterSeeking() {
        val data = ByteArray(32) { it.toByte() }
        VirtualMemory(32).use { memory ->
            memory.writeBytes(data)
            memory.source(size = 8, offset = 5).use { source ->
                val buffer = Buffer()
                assertEquals(3L, source.readAtMostTo(buffer, 3))
                assertEquals(5L, source.readAtMostTo(buffer, 20))
                assertContentEquals(data.copyOfRange(5, 13), buffer.readByteArray())
                assertEquals(-1L, source.readAtMostTo(buffer, 1))
                source.seek(-2, RandomAccess.Whence.END)
                assertEquals(2L, source.readAtMostTo(buffer, 8))
                assertContentEquals(data.copyOfRange(11, 13), buffer.readByteArray())
            }
        }
    }

    @Test
    fun zeroByteReadsDoNotChangePositionOrBuffer() {
        VirtualMemory(8).use { memory ->
            memory.source().use { source ->
                val buffer = Buffer().apply { write(byteArrayOf(42)) }
                assertEquals(0L, source.readAtMostTo(buffer, 0))
                assertEquals(0L, source.tell())
                source.seek(0, RandomAccess.Whence.END)
                assertEquals(0L, source.readAtMostTo(buffer, 0))
                assertEquals(8L, source.tell())
                assertContentEquals(byteArrayOf(42), buffer.readByteArray())
            }
        }
    }

    @Test
    fun emptyRegionIsExhausted() {
        VirtualMemory(8).use { memory ->
            memory.source(size = 0, offset = 8).use { source ->
                val buffer = Buffer()
                assertEquals(-1L, source.readAtMostTo(buffer, 1))
                assertEquals(0L, source.tell())
                assertEquals(0L, buffer.size)
            }
        }
    }

    @Test
    fun negativeByteCountIsRejectedWithoutConsumingData() {
        VirtualMemory(8).use { memory ->
            memory.source().use { source ->
                val buffer = Buffer()
                assertFailsWith<IllegalArgumentException> { source.readAtMostTo(buffer, -1) }
                assertEquals(0L, source.tell())
                assertEquals(0L, buffer.size)
            }
        }
    }

    @Test
    fun closeDoesNotOwnTheMemory() {
        VirtualMemory(8).use { memory ->
            val source = memory.source()
            source.close()
            memory.writeBytes(byteArrayOf(42))
            assertEquals(42.toByte(), memory.readAllBytes()[0])
        }
    }
}
