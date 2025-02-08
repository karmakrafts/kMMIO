/*
 * Copyright (C) Karma Krafts & associates 2025
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

package io.karma.mman

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import platform.posix.memcpy
import kotlin.math.min

@OptIn(UnsafeNumber::class)
@ExperimentalForeignApi
open class RawMemorySource(
    val address: COpaquePointer, val size: Long, val bufferSize: Int = PAGE_SIZE.toInt()
) : RawSource {
    protected var isClosed: Boolean = false

    var position: Long = 0
        protected set

    override fun readAtMostTo(
        sink: Buffer, byteCount: Long
    ): Long {
        if (isClosed || position == size) return -1L

        val buffer = ByteArray(bufferSize)
        val actualByteCount = min(
            size, byteCount
        )
        var readBytes = 0L

        while (readBytes < actualByteCount) {
            val chunkSize = min(
                bufferSize.toLong(), actualByteCount - readBytes
            )
            buffer.usePinned {
                memcpy(
                    it.addressOf(0), interpretCPointer<COpaque>(address.rawValue + position + readBytes),
                    chunkSize.convert()
                )
            }
            sink.write(
                buffer, endIndex = chunkSize.toInt()
            )
            readBytes += chunkSize
        }

        position += readBytes
        return readBytes
    }

    override fun close() {
        isClosed = true
    }
}