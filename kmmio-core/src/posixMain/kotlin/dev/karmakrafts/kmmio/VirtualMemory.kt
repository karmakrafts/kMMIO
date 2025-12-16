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
import kotlinx.io.files.SystemFileSystem
import platform.posix.O_CREAT
import platform.posix.ftruncate
import platform.posix.memcpy
import platform.posix.memset
import platform.posix.mlock
import platform.posix.mmap
import platform.posix.mprotect
import platform.posix.msync
import platform.posix.munlock
import platform.posix.munmap
import kotlin.math.min
import platform.posix.close as posixClose
import platform.posix.open as posixOpen

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
private class VirtualMemoryImpl(
    initialSize: Long,
    override val path: Path?,
    initialAccessFlags: AccessFlags,
    override val mappingFlags: MappingFlags
) : VirtualMemory {
    private var _size: Long = initialSize
    override val size: Long get() = _size

    private var _accessFlags: AccessFlags = initialAccessFlags
    override val accessFlags: AccessFlags = initialAccessFlags

    override val fileDescriptor: Int = run {
        if (path == null) return@run VirtualMemory.INVALID_FILE_DESCRIPTOR
        if (SystemFileSystem.exists(path)) SystemFileSystem.delete(path)
        SystemFileSystem.sink(path).close() // Create new file so we can resolve
        val fd = posixOpen(path.toString(), _accessFlags.toPosixOpenFlags() or O_CREAT)
        check(fd != VirtualMemory.INVALID_FILE_DESCRIPTOR) {
            "Could not open file for VirtualMemory at $path"
        }
        ftruncate(fd, initialSize.convert()).checkPosixResult()
        fd
    }

    private var _address: COpaquePointer = map()
    override val address: Long get() = _address.toLong()

    private fun map(): COpaquePointer = requireNotNull(
        mmap( // @formatter:off
            null,
            size.convert(),
            _accessFlags.toPosixFlags(),
            mappingFlags.toPosixFlags(),
            fileDescriptor,
            0
        ) // @formatter:on
    ) { "Could not map VirtualMemory" }

    private fun unmap() {
        munmap(_address, _size.convert()).checkPosixResult()
    }

    override fun zero() {
        memset(_address, 0x00, _size.convert())
    }

    override fun sync(flags: SyncFlags): Boolean {
        return msync(_address, _size.convert(), flags.value.toInt()) == 0
    }

    override fun lock(): Boolean = mlock(_address, _size.convert()) == 0

    override fun unlock(): Boolean = munlock(_address, _size.convert()) == 0

    override fun protect(accessFlags: AccessFlags): Boolean {
        return mprotect(_address, _size.convert(), accessFlags.toPosixFlags()) == 0
    }

    override fun resize(size: Long): Boolean {
        if (_size == size) return false
        unmap()
        if (isFileBacked) ftruncate(fileDescriptor, size.convert()).checkPosixResult()
        _address = map()
        return true
    }

    override fun copyTo(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, memory.size - dstOffset)
        memcpy( // @formatter:off
            (memory.address + dstOffset).toCPointer<COpaque>(),
            (address + srcOffset).toCPointer<COpaque>(),
            actualSize.convert()
        ) // @formatter:on
        return actualSize
    }

    override fun copyFrom(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, memory.size - dstOffset)
        memcpy( // @formatter:off
            (address + dstOffset).toCPointer<COpaque>(),
            (memory.address + srcOffset).toCPointer<COpaque>(),
            actualSize.convert()
        ) // @formatter:on
        return actualSize
    }

    override fun readBytes(array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, array.size.toLong() - dstOffset)
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
        val actualSize = min(size - srcOffset, array.size.toLong() - dstOffset)
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

actual fun VirtualMemory( // @formatter:off
    size: Long,
    path: Path?,
    accessFlags: AccessFlags,
    mappingFlags: MappingFlags
): VirtualMemory = VirtualMemoryImpl(size, path, accessFlags, mappingFlags) // @formatter:on

@OptIn(ExperimentalForeignApi::class)
actual fun VirtualMemory.getPointer(): COpaquePointer {
    check(this is VirtualMemoryImpl)
    return requireNotNull(address.toCPointer<COpaque>()) {
        "Could not get address of VirtualMemory"
    }
}