package io.karma.mman

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi

interface MemoryRegionHandle {
    @ExperimentalForeignApi
    val address: COpaquePointer
}

expect val PAGE_SIZE: Long

@ExperimentalForeignApi
internal expect fun isValidAddress(address: COpaquePointer?): Boolean

@ExperimentalForeignApi
internal expect fun mapMemory(fd: Int,
                              size: Long,
                              accessFlags: AccessFlags,
                              mappingFlags: MappingFlags): MemoryRegionHandle?

@ExperimentalForeignApi
internal expect fun unmapMemory(handle: MemoryRegionHandle, size: Long): Boolean

@ExperimentalForeignApi
internal expect fun lockMemory(handle: MemoryRegionHandle, size: Long): Boolean

@ExperimentalForeignApi
internal expect fun unlockMemory(handle: MemoryRegionHandle, size: Long): Boolean

@ExperimentalForeignApi
internal expect fun syncMemory(handle: MemoryRegionHandle, size: Long, flags: SyncFlags): Boolean

@ExperimentalForeignApi
internal expect fun protectMemory(handle: MemoryRegionHandle, size: Long, flags: AccessFlags): Boolean

@ExperimentalForeignApi
internal expect fun getLastError(): String