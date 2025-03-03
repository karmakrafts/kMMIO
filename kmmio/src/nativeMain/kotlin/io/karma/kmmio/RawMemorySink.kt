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

package io.karma.kmmio

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import platform.posix.memcpy
import kotlin.math.min

/**
 * A [RawSink] implementation which directly writes to an unmanaged memory block.
 *
 * @param address The base address of the memory block to write to.
 * @param size The total size of the memory block in bytes.
 * @param bufferSize The size of the internal buffer to be created in bytes.
 * @param flushCallback A function which is called everytime the sink is flushed.
 */
@OptIn(UnsafeNumber::class)
@ExperimentalForeignApi
open class RawMemorySink(
    val address: COpaquePointer,
    val size: Long,
    val bufferSize: Int = pageSize.toInt(),
    private val flushCallback: () -> Unit = {}
) : RawSink {
    protected var isClosed: Boolean = false
    protected var position: Long = 0

    override fun write(
        source: Buffer, byteCount: Long
    ) {
        if (isClosed) return

        val buffer = ByteArray(bufferSize)
        val actualByteCount = min(
            source.size, byteCount
        )
        require(actualByteCount <= size) { "Size exceeds memory region bounds" }
        var writtenBytes = 0L

        while (writtenBytes < actualByteCount) {
            var chunkSize = min(
                bufferSize.toLong(), actualByteCount - writtenBytes
            )
            chunkSize = source.readAtMostTo(
                buffer, endIndex = chunkSize.toInt()
            ).toLong()
            if (chunkSize == -1L) break // EOF
            buffer.usePinned {
                memcpy(
                    interpretCPointer<COpaque>(address.rawValue + position + writtenBytes),
                    it.addressOf(0),
                    chunkSize.convert()
                )
            }
            writtenBytes += chunkSize
        }

        position += writtenBytes
    }

    override fun flush() = flushCallback()

    override fun close() {
        isClosed = true
    }
}