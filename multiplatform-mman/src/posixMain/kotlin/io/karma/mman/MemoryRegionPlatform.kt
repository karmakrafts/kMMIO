package io.karma.mman

import kotlinx.cinterop.*
import platform.posix.*

/**
 * @author Alexander Hinze
 * @since 31/10/2024
 */

private inline val SyncFlags.posixValue: Int
    get() {
        var flags = 0
        if(SyncFlags.SYNC in this) flags = flags or MS_SYNC
        if(SyncFlags.ASYNC in this) flags = flags or MS_ASYNC
        if(SyncFlags.INVALIDATE in this) flags = flags or MS_INVALIDATE
        return flags
    }

private inline val AccessFlags.posixValue: Int
    get() {
        var flags = 0
        if(AccessFlags.READ in this) flags = flags or PROT_READ
        if(AccessFlags.WRITE in this) flags = flags or PROT_WRITE
        if(AccessFlags.EXEC in this) flags = flags or PROT_EXEC
        return flags
    }

private inline val MappingFlags.posixValue: Int
    get() {
        var flags = 0
        if(MappingFlags.ANON in this) flags = flags or MAP_ANON
        if(MappingFlags.PRIVATE in this) flags = flags or MAP_PRIVATE
        if(MappingFlags.SHARED in this) flags = flags or MAP_SHARED
        return flags
    }

actual val PAGE_SIZE: Long by lazy {
    getpagesize().toLong()
}

@ExperimentalForeignApi
internal actual fun isValidAddress(address: COpaquePointer?): Boolean = address != MAP_FAILED

@ExperimentalForeignApi
internal actual fun mapMemory(fd: Int,
                              size: Long,
                              accessFlags: AccessFlags,
                              mappingFlags: MappingFlags): COpaquePointer? {
    return mmap(null, size.convert(), accessFlags.posixValue, mappingFlags.posixValue, fd, 0)
}

@ExperimentalForeignApi
internal actual fun unmapMemory(address: COpaquePointer, size: Long): Boolean {
    return munmap(address, size.convert()) == 0
}

@ExperimentalForeignApi
internal actual fun lockMemory(address: COpaquePointer, size: Long): Boolean {
    return mlock(address, size.convert()) == 0
}

@ExperimentalForeignApi
internal actual fun unlockMemory(address: COpaquePointer, size: Long): Boolean {
    return munlock(address, size.convert()) == 0
}

@ExperimentalForeignApi
internal actual fun syncMemory(address: COpaquePointer, size: Long, flags: SyncFlags): Boolean {
    return msync(address, size.convert(), flags.posixValue) == 0
}

@ExperimentalForeignApi
internal actual fun protectMemory(address: COpaquePointer, size: Long, flags: AccessFlags): Boolean {
    return mprotect(address, size.convert(), flags.posixValue) == 0
}

@ExperimentalForeignApi
internal actual fun getLastError(): String = strerror(errno)?.toKString() ?: "Unknown error"