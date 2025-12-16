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

import com.sun.jna.Pointer
import kotlinx.io.files.Path
import java.io.File
import kotlin.math.min

private class VirtualMemoryImpl(
    initialSize: Long,
    override val path: Path?,
    initialAccessFlags: AccessFlags,
    override val mappingFlags: MappingFlags
) : VirtualMemory {
    override val fileDescriptor: Int = run {
        if (path == null) return@run VirtualMemory.INVALID_FILE_DESCRIPTOR
        val file = File(path.toString())
        if (file.exists()) file.createNewFile()
        val fd = file.absolutePath.allocateCStr().use { pathMemory ->
            LibC.open(pathMemory.getPointer(0L), initialAccessFlags.toPosixOpenFlags() or O_CREAT)
        }
        check(fd != VirtualMemory.INVALID_FILE_DESCRIPTOR) {
            "Could not open file for VirtualMemory at $path"
        }
        LibC.ftruncate(fd, initialSize).checkPosixResult()
        fd
    }

    private var _size: Long = initialSize
    override val size: Long get() = _size

    private var _accessFlags: AccessFlags = initialAccessFlags
    override val accessFlags: AccessFlags get() = _accessFlags

    private var _address: Pointer = map()
    override val address: Long get() = Pointer.nativeValue(_address)

    private fun map(): Pointer = requireNotNull(
        LibC.mmap(
            addr = null,
            length = _size,
            prot = accessFlags.toPosixFlags(),
            flags = mappingFlags.toPosixFlags(),
            fd = fileDescriptor,
            offset = 0L
        )
    ) {
        "Could not map VirtualMemory"
    }

    private fun unmap() {
        LibC.munmap(_address, _size).checkPosixResult()
    }

    override fun sync(flags: SyncFlags): Boolean = LibC.msync(_address, _size, flags.value.toInt()) == 0

    override fun lock(): Boolean = LibC.mlock(_address, _size) == 0

    override fun unlock(): Boolean = LibC.munlock(_address, _size) == 0

    override fun protect(accessFlags: AccessFlags): Boolean {
        return LibC.mprotect(_address, _size, accessFlags.toPosixFlags()) == 0
    }

    override fun resize(size: Long): Boolean {
        if (_size == size) return false // Don't resize if size is already the same
        unmap()
        if (isFileBacked) LibC.ftruncate(fileDescriptor, size).checkPosixResult()
        _address = map()
        return true
    }

    override fun zero() {
        LibC.memset(_address, 0x00, _size)
    }

    override fun copyTo(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, memory.size - dstOffset)
        LibC.memcpy( // @formatter:off
            Pointer.createConstant(memory.address + dstOffset),
            Pointer.createConstant(Pointer.nativeValue(_address) + srcOffset),
            actualSize
        ) // @formatter:on
        return actualSize
    }

    override fun copyFrom(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, memory.size - dstOffset)
        LibC.memcpy( // @formatter:off
            Pointer.createConstant(Pointer.nativeValue(_address) + dstOffset),
            Pointer.createConstant(memory.address + srcOffset),
            actualSize
        ) // @formatter:on
        return actualSize
    }

    override fun readBytes(array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, array.size.toLong() - dstOffset)
        _address.read(srcOffset, array, dstOffset.toInt(), actualSize.toInt())
        return actualSize
    }

    override fun writeBytes(array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, array.size.toLong() - dstOffset)
        _address.write(dstOffset, array, srcOffset.toInt(), actualSize.toInt())
        return actualSize
    }

    override fun close() {
        LibC.munmap(_address, _size).checkPosixResult()
        if (isFileBacked) LibC.close(fileDescriptor).checkPosixResult()
    }
}

actual fun VirtualMemory( // @formatter:off
    size: Long,
    path: Path?,
    accessFlags: AccessFlags,
    mappingFlags: MappingFlags
): VirtualMemory = VirtualMemoryImpl(size, path, accessFlags, mappingFlags) // @formatter:on

fun VirtualMemory.getPointer(offset: Long = 0L): Pointer = Pointer.createConstant(address + offset)