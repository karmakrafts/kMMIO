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

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toLong
import kotlinx.cinterop.usePinned
import kotlinx.io.files.Path
import platform.posix.O_CREAT
import platform.posix.ftruncate
import platform.posix.memcpy
import platform.posix.memset
import platform.posix.close as posixClose
import platform.posix.open as posixOpen

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal abstract class AbstractVirtualMemory(
    initialSize: Long,
    override val path: Path?,
    initialAccessFlags: AccessFlags,
    override val mappingFlags: MappingFlags
) : VirtualMemory {
    protected var _size: Long = initialSize
    override val size: Long get() = _size

    protected var _accessFlags: AccessFlags = initialAccessFlags
    override val accessFlags: AccessFlags get() = _accessFlags

    override val fileDescriptor: Int = run {
        val path = path
        if (path == null) return@run VirtualMemory.INVALID_FILE_DESCRIPTOR
        val fd =
            posixOpen(path.toString(), _accessFlags.toPosixOpenFlags() or O_CREAT, 0x1B6) // 0666, filtered by umask
        check(fd != VirtualMemory.INVALID_FILE_DESCRIPTOR) {
            "Could not open file for VirtualMemory at $path"
        }
        ftruncate(fd, initialSize.convert()).checkPosixResult()
        fd
    }

    protected lateinit var _address: COpaquePointer
    override val address: Long get() = _address.toLong()

    protected abstract fun map()
    protected abstract fun unmap()

    override fun zero() {
        memset(_address, 0x00, _size.convert())
    }

    override fun resize(size: Long): Boolean {
        if (_size == size) return false
        unmap()
        if (isFileBacked) ftruncate(fileDescriptor, size.convert()).checkPosixResult()
        _size = size
        map()
        return true
    }

    override fun copyTo(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = minOf(size, _size - srcOffset, memory.size - dstOffset)
        if (actualSize == 0L) return 0L
        memcpy( // @formatter:off
            (memory.address + dstOffset).toCPointer<COpaque>(),
            (address + srcOffset).toCPointer<COpaque>(),
            actualSize.convert()
        ) // @formatter:on
        return actualSize
    }

    override fun copyFrom(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = minOf(size, memory.size - srcOffset, _size - dstOffset)
        if (actualSize == 0L) return 0L
        memcpy( // @formatter:off
            (address + dstOffset).toCPointer<COpaque>(),
            (memory.address + srcOffset).toCPointer<COpaque>(),
            actualSize.convert()
        ) // @formatter:on
        return actualSize
    }

    override fun readBytes(array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = minOf(size, _size - srcOffset, array.size.toLong() - dstOffset)
        if (actualSize == 0L) return 0L
        array.usePinned { pinnedArray ->
            memcpy( // @formatter:off
                (pinnedArray.addressOf(0).toLong() + dstOffset).toCPointer<COpaque>(),
                (address + srcOffset).toCPointer<COpaque>(),
                actualSize.convert()
            ) // @formatter:on
        }
        return actualSize
    }

    override fun writeBytes(array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = minOf(size, array.size.toLong() - srcOffset, _size - dstOffset)
        if (actualSize == 0L) return 0L
        array.usePinned { pinnedArray ->
            memcpy( // @formatter:off
                (address + dstOffset).toCPointer<COpaque>(),
                (pinnedArray.addressOf(0).toLong() + srcOffset).toCPointer<COpaque>(),
                actualSize.convert()
            ) // @formatter:on
        }
        return actualSize
    }

    override fun close() {
        unmap()
        if (isFileBacked) posixClose(fileDescriptor)
    }
}