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

import kotlinx.io.files.Path
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.math.min

@PublishedApi
@Suppress("Since15") // We compile against Panama as a preview feature to be compatible with Java 21
internal class PosixVirtualMemory(
    initialSize: Long,
    override val path: Path?,
    initialAccessFlags: AccessFlags,
    override val mappingFlags: MappingFlags
) : VirtualMemory {
    companion object { // @formatter:off
        private val posixOpen: MethodHandle = getNativeFunction("open", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // const char* path
            ValueLayout.JAVA_INT // int oflags
            // ...
        ), Linker.Option.firstVariadicArg(2))
        private val posixClose: MethodHandle = getNativeFunction("close", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT // int fd
        ))
        private val ftruncate: MethodHandle = getNativeFunction("ftruncate", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, // int fd
            ValueLayout.JAVA_LONG // off_t length
        ))
        private val mmap: MethodHandle = getNativeFunction("mmap", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,   // void* addr
            ValueLayout.JAVA_LONG, // size_t length
            ValueLayout.JAVA_INT,  // int prot
            ValueLayout.JAVA_INT,  // int flags
            ValueLayout.JAVA_INT,  // int fd
            ValueLayout.JAVA_LONG  // off_t offset
        ))
        private val munmap: MethodHandle = getNativeFunction("munmap", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,  // void* addr
            ValueLayout.JAVA_LONG // size_t length
        ))
        private val mprotect: MethodHandle = getNativeFunction("mprotect", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,   // void* addr
            ValueLayout.JAVA_LONG, // size_t len
            ValueLayout.JAVA_INT   // int prot
        ))
        private val msync: MethodHandle = getNativeFunction("msync", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,   // void* addr
            ValueLayout.JAVA_LONG, // size_t len
            ValueLayout.JAVA_INT   // int flags
        ))
        private val mlock: MethodHandle = getNativeFunction("mlock", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,  // void* addr
            ValueLayout.JAVA_LONG // size_t len
        ))
        private val munlock: MethodHandle = getNativeFunction("munlock", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,  // void* addr
            ValueLayout.JAVA_LONG // size_t len
        ))
        private val memcpy: MethodHandle = getNativeFunction("memcpy", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,  // void* dst
            ValueLayout.ADDRESS,  // const void* src
            ValueLayout.JAVA_LONG // size_t size
        ))
        private val memset: MethodHandle = getNativeFunction("memset", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,  // void* addr
            ValueLayout.JAVA_INT, // int value (& 0xFF)
            ValueLayout.JAVA_LONG // size_t size
        ))
    } // @formatter:on

    private var _size: Long = initialSize
    override val size: Long get() = _size

    private var _accessFlags: AccessFlags = initialAccessFlags
    override val accessFlags: AccessFlags = initialAccessFlags

    override val fileDescriptor: Int = run {
        if (path == null) return@run VirtualMemory.INVALID_FILE_DESCRIPTOR // This virtual memory block is not file backed
        val nioPath = java.nio.file.Path.of(path.toString())
        Arena.ofConfined().use { arena ->
            if (nioPath.exists()) nioPath.deleteExisting()
            val pathAddress = arena.allocateUtf8String(nioPath.absolutePathString())
            val fd = posixOpen.invokeExact( // @formatter:off
                pathAddress,                                // const char* path
                _accessFlags.toPosixOpenFlags() or O_CREAT, // int oflags
            ) as Int // @formatter:on
            check(fd != VirtualMemory.INVALID_FILE_DESCRIPTOR) {
                "Could not open file for VirtualMemory at $path"
            }
            (ftruncate.invokeExact( // @formatter:off
                fd,   // int fd
                _size // off_t length
            ) as Int).checkPosixResult() // @formatter:on
            return@use fd
        }
    }

    private var _address: MemorySegment = map()
    override val address: Long get() = _address.address()

    private fun map(): MemorySegment = mmap.invokeExact( // @formatter:off
        MemorySegment.NULL,            // void* addr
        _size,                         // size_t length
        _accessFlags.toPosixFlags(),   // int prot
        mappingFlags.toPosixFlags(),   // int flags
        fileDescriptor,                // int fd
        0L                             // off_t offset
    ) as MemorySegment // @formatter:on

    private fun unmap() {
        (munmap.invokeExact( // @formatter:off
            _address, // void* addr
            _size     // size_t length
        ) as Int).checkPosixResult() // @formatter:on
    }

    override fun zero() {
        memset.invokeExact( // @formatter:off
            _address, // void* addr
            0x00,     // int value (& 0xFF)
            _size     // size_t size
        ) as MemorySegment // @formatter:on
    }

    override fun sync(flags: SyncFlags): Boolean = (msync.invokeExact( // @formatter:off
        _address,           // void* addr
        _size,              // size_t length
        flags.value.toInt() // int flags
    ) as Int) == 0 // @formatter:on

    override fun lock(): Boolean = (mlock.invokeExact( // @formatter:off
        _address, // void* addr
        _size,    // size_t length
    ) as Int) == 0 // @formatter:on

    override fun unlock(): Boolean = (munlock.invokeExact( // @formatter:off
        _address, // void* addr
        _size,    // size_t length
    ) as Int) == 0 // @formatter:on

    override fun protect(accessFlags: AccessFlags): Boolean = (mprotect.invokeExact(
        _address,                 // void* addr
        _size,                    // size_t length,
        accessFlags.value.toInt() // int prot
    ) as Int) == 0

    override fun resize(size: Long): Boolean {
        if (_size == size) return false // Don't resize if size is already the same
        unmap()
        if (isFileBacked) {
            // If the mapping is file backed, we need to unmap - resize - remap
            (ftruncate.invokeExact( // @formatter:off
                fileDescriptor, // int fd
                size            // off_t length
            ) as Int).checkPosixResult() // @formatter:on
        }
        _size = size
        _address = map()
        return true
    }

    override fun copyTo(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, memory.size - dstOffset)
        memcpy.invokeExact( // @formatter:off
            MemorySegment.ofAddress(memory.address + dstOffset), // void* dst
            MemorySegment.ofAddress(_address.address() + srcOffset),       // const void* src
            actualSize                                                     // size_t size
        ) as MemorySegment // @formatter:on
        return actualSize
    }

    override fun copyFrom(memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, memory.size - dstOffset)
        memcpy.invokeExact( // @formatter:off
            MemorySegment.ofAddress(_address.address() + dstOffset),       // void* dst
            MemorySegment.ofAddress(memory.address + srcOffset), // const void* src
            actualSize                                                     // size_t size
        ) as MemorySegment // @formatter:on
        return actualSize
    }

    override fun readBytes(array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, array.size.toLong() - dstOffset)
        val segment = MemorySegment.ofArray(array)
        memcpy.invokeExact( // @formatter:off
            MemorySegment.ofAddress(segment.address() + dstOffset),  // void* dst
            MemorySegment.ofAddress(_address.address() + srcOffset), // const void* src
            actualSize                                               // size_t size
        ) as MemorySegment // @formatter:on
        return actualSize
    }

    override fun writeBytes(array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long): Long {
        val actualSize = min(size - srcOffset, array.size.toLong() - dstOffset)
        val segment = MemorySegment.ofArray(array)
        memcpy.invokeExact( // @formatter:off
            MemorySegment.ofAddress(_address.address() + dstOffset), // void* dst
            MemorySegment.ofAddress(segment.address() + srcOffset),  // const void* src
            actualSize                                               // size_t size
        ) as MemorySegment // @formatter:on
        return actualSize
    }

    override fun close() {
        unmap()
        if (isFileBacked) {
            (posixClose.invokeExact(fileDescriptor) as Int).checkPosixResult()
        }
    }
}