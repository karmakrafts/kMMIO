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

package io.karma.mman

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import platform.posix._get_osfhandle
import platform.windows.CloseHandle
import platform.windows.CreateFileMappingW
import platform.windows.DWORD
import platform.windows.DWORDVar
import platform.windows.FILE_MAP_ALL_ACCESS
import platform.windows.FILE_MAP_EXECUTE
import platform.windows.FILE_MAP_READ
import platform.windows.FILE_MAP_WRITE
import platform.windows.FORMAT_MESSAGE_ALLOCATE_BUFFER
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FlushViewOfFile
import platform.windows.FormatMessageW
import platform.windows.GetLastError
import platform.windows.GetSystemInfo
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.InitializeSecurityDescriptor
import platform.windows.LANGID
import platform.windows.LANG_NEUTRAL
import platform.windows.LocalFree
import platform.windows.MapViewOfFileEx
import platform.windows.PAGE_EXECUTE_READ
import platform.windows.PAGE_EXECUTE_READWRITE
import platform.windows.PAGE_NOACCESS
import platform.windows.PAGE_READONLY
import platform.windows.PAGE_READWRITE
import platform.windows.PWSTRVar
import platform.windows.SECURITY_ATTRIBUTES
import platform.windows.SECURITY_DESCRIPTOR
import platform.windows.SECURITY_DESCRIPTOR_REVISION
import platform.windows.SUBLANG_DEFAULT
import platform.windows.SYSTEM_INFO
import platform.windows.TRUE
import platform.windows.UnmapViewOfFile
import platform.windows.VirtualLock
import platform.windows.VirtualProtect
import platform.windows.VirtualUnlock

@OptIn(ExperimentalForeignApi::class)
internal class WindowsMemoryRegionHandle(
    override val address: COpaquePointer, val mappingHandle: HANDLE, val securityDescriptor: SECURITY_ATTRIBUTES
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
internal actual fun mapMemory(
    fd: Int, size: Long, accessFlags: AccessFlags, mappingFlags: MappingFlags
): MemoryRegionHandle? = memScoped {
    val hiSize = (size shr 32) and 0xFFFFFFFF
    val loSize = size and 0xFFFFFFFF

    val securityDescriptor = nativeHeap.alloc<SECURITY_DESCRIPTOR>()
    if (InitializeSecurityDescriptor(
            securityDescriptor.ptr, SECURITY_DESCRIPTOR_REVISION.convert()
        ) == 0
    ) return@memScoped null
    val securityAttributes = nativeHeap.alloc<SECURITY_ATTRIBUTES> {
        nLength = sizeOf<SECURITY_ATTRIBUTES>().convert()
        lpSecurityDescriptor = securityDescriptor.ptr
        bInheritHandle = TRUE // Make sure child-processes can inherit the handle of this file
    }

    fun map(handle: HANDLE?): MemoryRegionHandle? {
        val mappingHandle = CreateFileMappingW(
            handle, securityAttributes.ptr, accessFlags.pageProtection, hiSize.convert(), loSize.convert(), null
        )
        if (mappingHandle == INVALID_HANDLE_VALUE) return null
        val address = MapViewOfFileEx(
            mappingHandle, accessFlags.mappingAccess,
            0.convert(), // Size must be derived from the mapping automatically
            0.convert(), 0.convert(), null
        )
        return if (mappingHandle == null || address == null) null
        else WindowsMemoryRegionHandle(
            address, mappingHandle, securityAttributes
        )
    }

    if (fd != -1) {
        val handle = _get_osfhandle(fd).toCPointer<COpaque>()
        if (handle == INVALID_HANDLE_VALUE) return@memScoped null
        return map(handle)
    } // Handle anonymous mappings in system page file
    return map(INVALID_HANDLE_VALUE)
}

@ExperimentalForeignApi
internal actual fun unmapMemory(
    handle: MemoryRegionHandle, size: Long
): Boolean {
    require(handle is WindowsMemoryRegionHandle) { "Handle must be WindowsMemoryRegionHandle" }
    if (UnmapViewOfFile(handle.address) == 0) return false
    if (CloseHandle(handle.mappingHandle) == 0) return false
    handle.securityDescriptor.lpSecurityDescriptor?.let(nativeHeap::free)
    nativeHeap.free(handle.securityDescriptor)
    return true
}

@ExperimentalForeignApi
internal actual fun lockMemory(
    handle: MemoryRegionHandle, size: Long
): Boolean {
    require(handle is WindowsMemoryRegionHandle) { "Handle must be WindowsMemoryRegionHandle" }
    return VirtualLock(
        handle.address, size.convert()
    ) != 0
}

@ExperimentalForeignApi
internal actual fun unlockMemory(
    handle: MemoryRegionHandle, size: Long
): Boolean {
    require(handle is WindowsMemoryRegionHandle) { "Handle must be WindowsMemoryRegionHandle" }
    return VirtualUnlock(
        handle.address, size.convert()
    ) != 0
}

@ExperimentalForeignApi
internal actual fun syncMemory(
    handle: MemoryRegionHandle, size: Long, flags: SyncFlags
): Boolean {
    require(
        handle is WindowsMemoryRegionHandle
    ) { "Handle must be WindowsMemoryRegionHandle" } // Flags are completely ignored on Windows at the moment
    return FlushViewOfFile(
        handle.address, size.convert()
    ) != 0
}

@ExperimentalForeignApi
internal actual fun protectMemory(
    handle: MemoryRegionHandle, size: Long, flags: AccessFlags
): Boolean = memScoped {
    require(handle is WindowsMemoryRegionHandle) { "Handle must be WindowsMemoryRegionHandle" }
    val oldPageProtection = alloc<DWORDVar>()
    return VirtualProtect(
        handle.address, size.convert(), flags.pageProtection, oldPageProtection.ptr
    ) != 0
}

/*
 * As macros can't really be expanded by cinterop, we write this ourselves;
 * 10 first bits are used for the secondary language identifier, while the remaining 6 are the primary one.
 */
@ExperimentalForeignApi
private fun makeLangId(
    primary: Int, secondary: Int
): LANGID = ((secondary shl 10) or primary).convert()

@ExperimentalForeignApi
private val defaultLangId: LANGID = makeLangId(
    LANG_NEUTRAL, SUBLANG_DEFAULT
)

@ExperimentalForeignApi
private val defaultMessageFlags: DWORD = (FORMAT_MESSAGE_ALLOCATE_BUFFER or FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS).convert()

@ExperimentalForeignApi
internal actual fun getLastError(): String = memScoped {
    val error = GetLastError()
    if (error.convert<Int>() == 0) return@memScoped "Unknown error"
    val buffer = allocPointerTo<PWSTRVar>()
    if (FormatMessageW(
            defaultMessageFlags, null, error, defaultLangId.convert(), buffer.ptr.reinterpret(), 0.convert(), null
        ).convert<Int>() == 0
    ) return@memScoped "Unknown error"
    val message = buffer.pointed?.value?.toKStringFromUtf16()
    LocalFree(buffer.value) // Since Windows allocates this message on the heap, explicitly free it
    message ?: "Unknown error"
}