/*
 * Copyright 2025 Karma Krafts
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
import kotlin.math.min

private class VirtualMemorySource( // @formatter:off
    private val memory: VirtualMemory,
    private val size: Long,
    private val offset: Long
) : RandomAccessSource { // @formatter:on
    private var position: Long = 0L
    private val buffer: ByteArray = ByteArray(8192)

    override fun seek(offset: Long, whence: RandomAccess.Whence) = when (whence) {
        RandomAccess.Whence.START -> position = offset
        RandomAccess.Whence.CURRENT -> position += offset
        RandomAccess.Whence.END -> position = size + offset
    }

    override fun tell(): Long = position

    override fun close() = Unit // no-op: does not own the memory

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (position >= size) return -1L

        var remaining = min(byteCount, size - position)
        var totalRead = 0L
        while (remaining > 0L) {
            val chunk = min(remaining, buffer.size.toLong()).toInt()
            val read = memory.readBytes(buffer, size = chunk.toLong(), srcOffset = offset + position, dstOffset = 0L)
            if (read <= 0L) break
            sink.write(buffer, 0, read.toInt())
            position += read
            totalRead += read
            remaining -= read
            if (read < chunk) break // guard against partial read
        }
        if (totalRead == 0L) return -1L
        return totalRead
    }
}

/**
 * Creates a new [RandomAccessSource] for this [VirtualMemory] block.
 *
 * @param size the size of the region to read from
 * @param offset the offset in the virtual memory block to start reading from
 * @return the new [RandomAccessSource]
 */
fun VirtualMemory.source(size: Long = this.size, offset: Long = 0L): RandomAccessSource =
    VirtualMemorySource(this, size, offset)