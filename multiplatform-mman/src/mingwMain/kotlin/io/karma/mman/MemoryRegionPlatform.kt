package io.karma.mman

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.windows.GetSystemInfo
import platform.windows.SYSTEM_INFO

/**
 * @author Alexander Hinze
 * @since 30/10/2024
 */

@OptIn(ExperimentalForeignApi::class)
actual val PAGE_SIZE: Long by lazy {
    memScoped {
        val info = alloc<SYSTEM_INFO>()
        GetSystemInfo(info.ptr)
        info.dwPageSize.toLong()
    }
}

@ExperimentalForeignApi
internal actual fun isValidAddress(address: COpaquePointer?): Boolean = address != null

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