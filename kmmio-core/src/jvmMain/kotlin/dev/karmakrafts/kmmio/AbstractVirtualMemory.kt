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

import kotlinx.io.files.Path
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.math.min
import java.nio.file.Path as NioPath

@Suppress("Since15") // We compile against Panama as a preview feature to be compatible with Java 21
internal abstract class AbstractVirtualMemory(
    initialSize: Long,
    override val path: Path?,
    initialAccessFlags: AccessFlags,
    override val mappingFlags: MappingFlags
) : VirtualMemory {
    companion object { // @formatter:off
        private val memset: MethodHandle = getNativeFunction("memset", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,  // void* addr
            ValueLayout.JAVA_INT, // int value (& 0xFF)
            ValueLayout.JAVA_LONG // size_t size
        ))
        private val memcpy: MethodHandle = getNativeFunction("memcpy", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,  // void* dst
            ValueLayout.ADDRESS,  // const void* src
            ValueLayout.JAVA_LONG // size_t size
        ))
    } // @formatter:on

    protected var _size: Long = initialSize
    override val size: Long get() = _size

    protected var _accessFlags: AccessFlags = initialAccessFlags
    override val accessFlags: AccessFlags = _accessFlags

    protected lateinit var _address: MemorySegment
    override val address: Long get() = _address.address()

    protected abstract fun openFile(name: MemorySegment, flags: Int, mask: Int): Int
    protected abstract fun closeFile(fd: Int): Int
    protected abstract fun truncateFile(fd: Int, size: Long): Int
    protected abstract fun map()
    protected abstract fun unmap()

    override val fileDescriptor: Int = run {
        if (path == null) return@run VirtualMemory.INVALID_FILE_DESCRIPTOR // This virtual memory block is not file backed
        val nioPath = NioPath.of(path.toString())
        if (nioPath.exists()) nioPath.deleteExisting()
        Arena.ofConfined().use { arena ->
            val pathAddress = arena.allocateUtf8String(nioPath.absolutePathString())
            val fd = openFile( // @formatter:off
                pathAddress,
                _accessFlags.toPosixOpenFlags() or O_CREAT,
                _accessFlags.toPosixFileMask()
            ) // @formatter:on
            check(fd != VirtualMemory.INVALID_FILE_DESCRIPTOR) {
                "Could not open file for VirtualMemory at $path"
            }
            truncateFile(fd, initialSize).checkPosixResult()
            fd
        }
    }

    override fun resize(size: Long): Boolean {
        if (_size == size) return false // Don't resize if size is already the same
        unmap()
        if (isFileBacked) {
            // If the mapping is file backed, we need to unmap - resize - remap
            truncateFile(fileDescriptor, size).checkPosixResult()
        }
        _size = size
        map()
        return true
    }

    override fun zero() {
        memset.invokeExact(_address, 0x00, _size) as MemorySegment
    }

    override fun copyTo(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, memory.size - dstOffset)
        memcpy.invokeExact( // @formatter:off
            MemorySegment.ofAddress(memory.address + dstOffset),
            MemorySegment.ofAddress(_address.address() + srcOffset),
            actualSize
        ) as MemorySegment // @formatter:on
        return actualSize
    }

    override fun copyFrom(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, memory.size - dstOffset)
        memcpy.invokeExact( // @formatter:off
            MemorySegment.ofAddress(_address.address() + dstOffset),
            MemorySegment.ofAddress(memory.address + srcOffset),
            actualSize
        ) as MemorySegment // @formatter:on
        return actualSize
    }

    override fun readBytes(array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, array.size.toLong() - dstOffset)
        _address.reinterpret(_size)
            .asSlice(srcOffset, _size - srcOffset)
            .asByteBuffer()
            .get(array, dstOffset.toInt(), size.toInt() - dstOffset.toInt())
        return actualSize
    }

    override fun writeBytes(array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, _size - dstOffset)
        _address.reinterpret(_size)
            .asSlice(dstOffset, _size - dstOffset)
            .asByteBuffer()
            .put(array, srcOffset.toInt(), (size - srcOffset).toInt())
        return actualSize
    }

    override fun close() {
        unmap()
        if (isFileBacked) {
            closeFile(fileDescriptor).checkPosixResult()
        }
    }
}