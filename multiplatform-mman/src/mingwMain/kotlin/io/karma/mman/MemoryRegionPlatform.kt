package io.karma.mman

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKStringFromUtf16
import platform.posix._get_osfhandle
import platform.windows.CreateFileMappingW
import platform.windows.DWORD
import platform.windows.FILE_MAP_ALL_ACCESS
import platform.windows.FILE_MAP_EXECUTE
import platform.windows.FILE_MAP_READ
import platform.windows.FILE_MAP_WRITE
import platform.windows.FILE_NAME_NORMALIZED
import platform.windows.GetFinalPathNameByHandleW
import platform.windows.GetSystemInfo
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.MAX_PATH
import platform.windows.MapViewOfFileEx
import platform.windows.PAGE_EXECUTE_READ
import platform.windows.PAGE_EXECUTE_READWRITE
import platform.windows.PAGE_NOACCESS
import platform.windows.PAGE_READONLY
import platform.windows.PAGE_READWRITE
import platform.windows.SYSTEM_INFO
import platform.windows.WCHARVar

/**
 * @author Alexander Hinze
 * @since 30/10/2024
 */

@OptIn(ExperimentalForeignApi::class)
internal class WindowsMemoryRegionHandle(
    override val address: COpaquePointer,
    val mappingHandle: HANDLE
) : MemoryRegionHandle

@OptIn(ExperimentalForeignApi::class)
private inline val AccessFlags.pageProtection: DWORD
    get() = when {
        AccessFlags.EXEC in this && AccessFlags.READ in this && AccessFlags.WRITE in this -> PAGE_EXECUTE_READWRITE
        AccessFlags.EXEC in this && AccessFlags.READ in this -> PAGE_EXECUTE_READ
        AccessFlags.READ in this && AccessFlags.WRITE in this -> PAGE_READWRITE
        AccessFlags.READ in this -> PAGE_READONLY
        else -> PAGE_NOACCESS
    }.convert()

@OptIn(ExperimentalForeignApi::class)
private inline val AccessFlags.mappingAccess: DWORD
    get() {
        var result = when {
            AccessFlags.READ in this && AccessFlags.WRITE in this -> FILE_MAP_ALL_ACCESS
            AccessFlags.WRITE in this -> FILE_MAP_WRITE
            AccessFlags.READ in this -> FILE_MAP_READ
            else -> 0
        }
        if (AccessFlags.EXEC in this) result = result or FILE_MAP_EXECUTE
        return result.convert()
    }

@OptIn(ExperimentalForeignApi::class)
actual val PAGE_SIZE: Long by lazy {
    memScoped {
        val info = alloc<SYSTEM_INFO>()
        GetSystemInfo(info.ptr)
        info.dwPageSize.toLong()
    }
}

@ExperimentalForeignApi
internal actual fun mapMemory(fd: Int,
                              size: Long,
                              accessFlags: AccessFlags,
                              mappingFlags: MappingFlags): MemoryRegionHandle? = memScoped {
    val loSize = size and 0xFFFFFFFF
    val hiSize = (size shr 32) and 0xFFFFFFFF
    if (fd != -1) {
        val handle = _get_osfhandle(fd).toCPointer<COpaque>()
        require(handle != INVALID_HANDLE_VALUE) { "Could not retrieve OS handle for FD $fd" }

        val pathBuffer = allocArray<WCHARVar>(MAX_PATH)
        require(GetFinalPathNameByHandleW(handle, pathBuffer, MAX_PATH.convert(), FILE_NAME_NORMALIZED.convert()).convert<Int>() != 0) { "Could not retrieve file path for FD $fd" }
        val path = pathBuffer.toKStringFromUtf16()

        val mappingHandle = CreateFileMappingW(handle, null, accessFlags.pageProtection, 0.convert(), 0.convert(), null)
        require(mappingHandle != INVALID_HANDLE_VALUE) { "Could not create file mapping for $path" }
        val address = MapViewOfFileEx(mappingHandle, accessFlags.mappingAccess, hiSize.convert(), loSize.convert(), 0.convert(), null)

        return if(mappingHandle == null || address == null) null
        else WindowsMemoryRegionHandle(
            address,
            mappingHandle
        )
    }
    // Handle anonymous mappings in system page file
    val mappingHandle = CreateFileMappingW(INVALID_HANDLE_VALUE, null, accessFlags.pageProtection, 0.convert(), 0.convert(), null)
    require(mappingHandle != INVALID_HANDLE_VALUE) { "Could not create anonymous file mapping" }
    val address = MapViewOfFileEx(mappingHandle, accessFlags.mappingAccess, hiSize.convert(), loSize.convert(), 0.convert(), null)

    return if(mappingHandle == null || address == null) null
    else WindowsMemoryRegionHandle(
        address,
        mappingHandle
    )
}

@ExperimentalForeignApi
internal actual fun unmapMemory(handle: MemoryRegionHandle, size: Long): Boolean = false

@ExperimentalForeignApi
internal actual fun lockMemory(handle: MemoryRegionHandle, size: Long): Boolean = false

@ExperimentalForeignApi
internal actual fun unlockMemory(handle: MemoryRegionHandle, size: Long): Boolean = false

@ExperimentalForeignApi
internal actual fun syncMemory(handle: MemoryRegionHandle, size: Long, flags: SyncFlags): Boolean = false

@ExperimentalForeignApi
internal actual fun protectMemory(handle: MemoryRegionHandle, size: Long, flags: AccessFlags): Boolean = false

@ExperimentalForeignApi
internal actual fun getLastError(): String = ""