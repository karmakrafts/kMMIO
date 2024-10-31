package io.karma.mman

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * @author Alexander Hinze
 * @since 31/10/2024
 */

@ExperimentalForeignApi
internal actual fun mapMemory(fd: Int,
                              size: Long,
                              accessFlags: AccessFlags,
                              mappingFlags: MappingFlags): COpaquePointer? = null

@ExperimentalForeignApi
internal actual fun unmapMemory(address: COpaquePointer, size: Long): Boolean = false

@ExperimentalForeignApi
internal actual fun lockMemory(address: COpaquePointer, size: Long): Boolean = false

@ExperimentalForeignApi
internal actual fun unlockMemory(address: COpaquePointer, size: Long): Boolean = false

@ExperimentalForeignApi
internal actual fun syncMemory(address: COpaquePointer, size: Long, flags: SyncFlags): Boolean = false

@ExperimentalForeignApi
internal actual fun protectMemory(address: COpaquePointer, size: Long, flags: AccessFlags): Boolean = false

@ExperimentalForeignApi
internal actual fun getLastError(): String = ""