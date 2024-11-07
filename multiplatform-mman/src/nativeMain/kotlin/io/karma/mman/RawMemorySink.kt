package io.karma.mman

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import platform.posix.memcpy
import kotlin.math.min

/**
 * @author Alexander Hinze
 * @since 07/11/2024
 */
@ExperimentalForeignApi
open class RawMemorySink(
    val address: COpaquePointer,
    val size: Long,
    val bufferSize: Int = PAGE_SIZE.toInt(),
    private inline val flushCallback: () -> Unit = {}
) : RawSink {
    protected var isClosed: Boolean = false
    protected var position: Long = 0

    override fun write(
        source: Buffer,
        byteCount: Long
    ) {
        if (isClosed) return

        val buffer = ByteArray(bufferSize)
        val actualByteCount = min(
            source.size,
            byteCount
        )
        require(actualByteCount <= size) { "Size exceeds memory region bounds" }
        var writtenBytes = 0L

        while (writtenBytes < actualByteCount) {
            var chunkSize = min(
                bufferSize.toLong(),
                actualByteCount - writtenBytes
            )
            chunkSize = source.readAtMostTo(
                buffer,
                endIndex = chunkSize.toInt()
            )
                .toLong()
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