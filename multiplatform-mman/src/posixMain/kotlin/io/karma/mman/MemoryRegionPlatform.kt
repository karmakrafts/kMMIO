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

@file:OptIn(UnsafeNumber::class)

package io.karma.mman

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import platform.posix.MAP_ANON
import platform.posix.MAP_PRIVATE
import platform.posix.MAP_SHARED
import platform.posix.MS_ASYNC
import platform.posix.MS_INVALIDATE
import platform.posix.MS_SYNC
import platform.posix.PROT_EXEC
import platform.posix.PROT_READ
import platform.posix.PROT_WRITE
import platform.posix.errno
import platform.posix.getpagesize
import platform.posix.mlock
import platform.posix.mmap
import platform.posix.mprotect
import platform.posix.msync
import platform.posix.munlock
import platform.posix.munmap
import platform.posix.strerror

@OptIn(ExperimentalForeignApi::class)
internal class PosixMemoryRegionHandle(
    override val address: COpaquePointer
) : MemoryRegionHandle

private inline val SyncFlags.posixValue: Int
    get() {
        var flags = 0
        if (SyncFlags.SYNC in this) flags = flags or MS_SYNC
        if (SyncFlags.ASYNC in this) flags = flags or MS_ASYNC
        if (SyncFlags.INVALIDATE in this) flags = flags or MS_INVALIDATE
        return flags
    }

private inline val AccessFlags.posixValue: Int
    get() {
        var flags = 0
        if (AccessFlags.READ in this) flags = flags or PROT_READ
        if (AccessFlags.WRITE in this) flags = flags or PROT_WRITE
        if (AccessFlags.EXEC in this) flags = flags or PROT_EXEC
        return flags
    }

private inline val MappingFlags.posixValue: Int
    get() {
        var flags = 0
        if (MappingFlags.ANON in this) flags = flags or MAP_ANON
        if (MappingFlags.PRIVATE in this) flags = flags or MAP_PRIVATE
        if (MappingFlags.SHARED in this) flags = flags or MAP_SHARED
        return flags
    }

actual val pageSize: Long by lazy {
    getpagesize().toLong()
}

@ExperimentalForeignApi
internal actual fun mapMemory(
    fd: Int, size: Long, accessFlags: AccessFlags, mappingFlags: MappingFlags
): MemoryRegionHandle? {
    return mmap(
        null, size.convert(), accessFlags.posixValue, mappingFlags.posixValue, fd, 0
    )?.let {
        PosixMemoryRegionHandle(it)
    }
}

@ExperimentalForeignApi
internal actual fun unmapMemory(
    handle: MemoryRegionHandle, size: Long
): Boolean {
    require(handle is PosixMemoryRegionHandle) { "Handle must be a PosixMemoryRegionHandle" }
    return munmap(
        handle.address, size.convert()
    ) == 0
}

@ExperimentalForeignApi
internal actual fun lockMemory(
    handle: MemoryRegionHandle, size: Long
): Boolean {
    require(handle is PosixMemoryRegionHandle) { "Handle must be a PosixMemoryRegionHandle" }
    return mlock(
        handle.address, size.convert()
    ) == 0
}

@ExperimentalForeignApi
internal actual fun unlockMemory(
    handle: MemoryRegionHandle, size: Long
): Boolean {
    require(handle is PosixMemoryRegionHandle) { "Handle must be a PosixMemoryRegionHandle" }
    return munlock(
        handle.address, size.convert()
    ) == 0
}

@ExperimentalForeignApi
internal actual fun syncMemory(
    handle: MemoryRegionHandle, size: Long, flags: SyncFlags
): Boolean {
    require(handle is PosixMemoryRegionHandle) { "Handle must be a PosixMemoryRegionHandle" }
    return msync(
        handle.address, size.convert(), flags.posixValue
    ) == 0
}

@ExperimentalForeignApi
internal actual fun protectMemory(
    handle: MemoryRegionHandle, size: Long, flags: AccessFlags
): Boolean {
    require(handle is PosixMemoryRegionHandle) { "Handle must be a PosixMemoryRegionHandle" }
    return mprotect(
        handle.address, size.convert(), flags.posixValue
    ) == 0
}

@ExperimentalForeignApi
internal actual fun getLastError(): String? = strerror(errno)?.toKString()