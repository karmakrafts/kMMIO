package io.karma.mman

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi

expect val PAGE_SIZE: Long

@ExperimentalForeignApi
internal expect fun isValidAddress(address: COpaquePointer?): Boolean

@ExperimentalForeignApi
internal expect fun mapMemory(fd: Int,
                              size: Long,
                              accessFlags: AccessFlags,
                              mappingFlags: MappingFlags): COpaquePointer?

@ExperimentalForeignApi
internal expect fun unmapMemory(address: COpaquePointer, size: Long): Boolean

@ExperimentalForeignApi
internal expect fun lockMemory(address: COpaquePointer, size: Long): Boolean

@ExperimentalForeignApi
internal expect fun unlockMemory(address: COpaquePointer, size: Long): Boolean

@ExperimentalForeignApi
internal expect fun syncMemory(address: COpaquePointer, size: Long, flags: SyncFlags): Boolean

@ExperimentalForeignApi
internal expect fun protectMemory(address: COpaquePointer, size: Long, flags: AccessFlags): Boolean

@ExperimentalForeignApi
internal expect fun getLastError(): String