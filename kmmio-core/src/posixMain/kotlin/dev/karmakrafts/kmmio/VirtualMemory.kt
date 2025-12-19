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

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.io.files.Path
import platform.posix.mlock
import platform.posix.mmap
import platform.posix.mprotect
import platform.posix.msync
import platform.posix.munlock
import platform.posix.munmap

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
private class VirtualMemoryImpl( // @formatter:off
    initialSize: Long,
    path: Path?,
    initialAccessFlags: AccessFlags,
    mappingFlags: MappingFlags
) : AbstractVirtualMemory(initialSize, path, initialAccessFlags, mappingFlags) { // @formatter:on
    init {
        map()
    }

    override fun map() {
        _address = requireNotNull(
            mmap( // @formatter:off
                null,
                size.convert(),
                _accessFlags.toPosixFlags(),
                mappingFlags.toPosixFlags(),
                fileDescriptor,
                0
            ) // @formatter:on
        ) { "Could not map VirtualMemory" }
    }

    override fun unmap() {
        munmap(_address, _size.convert()).checkPosixResult()
    }

    override fun sync(flags: SyncFlags): Boolean {
        return msync(_address, _size.convert(), flags.value.toInt()) == 0
    }

    override fun lock(): Boolean = mlock(_address, _size.convert()) == 0

    override fun unlock(): Boolean = munlock(_address, _size.convert()) == 0

    override fun protect(accessFlags: AccessFlags): Boolean {
        return mprotect(_address, _size.convert(), accessFlags.toPosixFlags()) == 0
    }
}

actual fun VirtualMemory( // @formatter:off
    size: Long,
    path: Path?,
    accessFlags: AccessFlags,
    mappingFlags: MappingFlags
): VirtualMemory = VirtualMemoryImpl(size, path, accessFlags, mappingFlags) // @formatter:on