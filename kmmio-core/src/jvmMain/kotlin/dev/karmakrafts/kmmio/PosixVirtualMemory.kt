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
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

@PublishedApi
@Suppress("Since15") // We compile against Panama as a preview feature to be compatible with Java 21
internal class PosixVirtualMemory( // @formatter:off
    initialSize: Long,
    path: Path?,
    initialAccessFlags: AccessFlags,
    mappingFlags: MappingFlags
) : AbstractVirtualMemory(initialSize, path, initialAccessFlags, mappingFlags) { // @formatter:on
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
    } // @formatter:on

    init {
        map()
    }

    override fun openFile(name: MemorySegment, flags: Int, mask: Int): Int =
        posixOpen.invokeExact(name, flags, mask) as Int

    override fun closeFile(fd: Int): Int = posixClose.invokeExact(fd) as Int
    override fun truncateFile(fd: Int, size: Long): Int = ftruncate.invokeExact(fd, size) as Int

    override fun map() {
        _address = mmap.invokeExact( // @formatter:off
            MemorySegment.NULL,
            _size,
            _accessFlags.toPosixFlags(),
            mappingFlags.toPosixFlags(),
            fileDescriptor,
            0L
        ) as MemorySegment // @formatter:on
    }

    override fun unmap() {
        (munmap.invokeExact(_address, _size) as Int).checkPosixResult()
    }

    override fun sync(flags: SyncFlags): Boolean = (msync.invokeExact( // @formatter:off
        _address,
        _size,
        flags.value.toInt()
    ) as Int) == 0 // @formatter:on

    override fun lock(): Boolean = (mlock.invokeExact( // @formatter:off
        _address,
        _size,
    ) as Int) == 0 // @formatter:on

    override fun unlock(): Boolean = (munlock.invokeExact( // @formatter:off
        _address,
        _size,
    ) as Int) == 0 // @formatter:on

    override fun protect(accessFlags: AccessFlags): Boolean {
        if (_accessFlags == accessFlags) return false
        val result = (mprotect.invokeExact(
            _address, _size, accessFlags.value.toInt()
        ) as Int) == 0
        _accessFlags = accessFlags
        return result
    }
}