package io.karma.mman

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import platform.posix.memcpy
import kotlin.math.min

/**
 * @author Alexander Hinze
 * @since 07/11/2024
 */
@ExperimentalForeignApi
open class RawMemorySource(
    val address: COpaquePointer,
    val size: Long,
    val bufferSize: Int = PAGE_SIZE.toInt()
) : RawSource {
    protected var isClosed: Boolean = false

    var position: Long = 0
        protected set

    override fun readAtMostTo(
        sink: Buffer,
        byteCount: Long
    ): Long {
        if (isClosed || position == size) return -1L

        val buffer = ByteArray(bufferSize)
        val actualByteCount = min(
            size,
            byteCount
        )
        var readBytes = 0L

        while (readBytes < actualByteCount) {
            val chunkSize = min(
                bufferSize.toLong(),
                actualByteCount - readBytes
            )
            buffer.usePinned {
                memcpy(
                    it.addressOf(0),
                    interpretCPointer<COpaque>(address.rawValue + position + readBytes),
                    chunkSize.convert()
                )
            }
            sink.write(
                buffer,
                endIndex = chunkSize.toInt()
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