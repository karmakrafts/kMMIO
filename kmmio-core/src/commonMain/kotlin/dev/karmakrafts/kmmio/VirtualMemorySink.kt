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

private class VirtualMemorySink( // @formatter:off
    private val memory: VirtualMemory,
    private val size: Long,
    private val offset: Long
) : RandomAccessSink { // @formatter:on
    private var position: Long = 0L
    private val buffer: ByteArray = ByteArray(8192)

    override fun close() = Unit // no-op: does not own the memory

    override fun seek(offset: Long, whence: RandomAccess.Whence) = when (whence) {
        RandomAccess.Whence.START -> position = offset
        RandomAccess.Whence.CURRENT -> position += offset
        RandomAccess.Whence.END -> position = size + offset
    }

    override fun tell(): Long = position

    override fun flush() {
        if (memory.isFileBacked) memory.sync(SyncFlags.SYNC)
    }

    override fun write(source: Buffer, byteCount: Long) {
        val remaining = size - position
        require(byteCount <= remaining) {
            "Not enough space in VirtualMemory region: requested $byteCount bytes, $remaining bytes were remaining"
        }
        var toWrite = byteCount
        while (toWrite > 0L) {
            val chunk = min(toWrite, buffer.size.toLong()).toInt()
            val read = source.readAtMostTo(buffer, 0, chunk)
            if (read <= 0) break
            val wrote = memory.writeBytes(buffer, read.toLong(), srcOffset = 0L, dstOffset = offset + position)
            position += wrote
            toWrite -= wrote
            if (wrote < read) break // Shouldn't happen, but guard against partial write
        }
        check(toWrite == 0L) { "Source did not provide enough data: $toWrite bytes remaining" }
    }
}

/**
 * Creates a new [RandomAccessSink] for this [VirtualMemory] block.
 *
 * @param size the size of the region to write to
 * @param offset the offset in the virtual memory block to start writing at
 * @return the new [RandomAccessSink]
 */
fun VirtualMemory.sink(size: Long = this.size, offset: Long = 0L): RandomAccessSink =
    VirtualMemorySink(this, size, offset)