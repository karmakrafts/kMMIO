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

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.toCPointer
import kotlinx.io.files.Path
import platform.posix._get_osfhandle
import platform.windows.CloseHandle
import platform.windows.CreateFileMappingW
import platform.windows.FILE_MAP_ALL_ACCESS
import platform.windows.FILE_MAP_EXECUTE
import platform.windows.FILE_MAP_READ
import platform.windows.FILE_MAP_WRITE
import platform.windows.FlushViewOfFile
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.MapViewOfFileEx
import platform.windows.PAGE_EXECUTE_READ
import platform.windows.PAGE_EXECUTE_READWRITE
import platform.windows.PAGE_NOACCESS
import platform.windows.PAGE_READONLY
import platform.windows.PAGE_READWRITE
import platform.windows.UnmapViewOfFile
import platform.windows.VirtualLock
import platform.windows.VirtualProtect
import platform.windows.VirtualUnlock

@OptIn(ExperimentalForeignApi::class)
private class VirtualMemoryImpl(
    initialSize: Long, path: Path?, initialAccessFlags: AccessFlags, mappingFlags: MappingFlags
) : AbstractVirtualMemory(initialSize, path, initialAccessFlags, mappingFlags) {
    companion object {
        private fun AccessFlags.toPageProtectionFlags(): Int = when {
            AccessFlags.EXEC in this && AccessFlags.READ in this && AccessFlags.WRITE in this -> PAGE_EXECUTE_READWRITE
            AccessFlags.EXEC in this && AccessFlags.READ in this -> PAGE_EXECUTE_READ
            AccessFlags.READ in this && AccessFlags.WRITE in this -> PAGE_READWRITE
            AccessFlags.READ in this -> PAGE_READONLY
            else -> PAGE_NOACCESS
        }

        private fun AccessFlags.toMappingFlags(): Int {
            var result = when {
                AccessFlags.READ in this && AccessFlags.WRITE in this -> FILE_MAP_ALL_ACCESS
                AccessFlags.WRITE in this -> FILE_MAP_WRITE
                AccessFlags.READ in this -> FILE_MAP_READ
                else -> 0
            }
            if (AccessFlags.EXEC in this) result = result or FILE_MAP_EXECUTE
            return result
        }
    }

    private lateinit var mappingHandle: HANDLE

    init {
        map()
    }

    override fun map() {
        val handle = if (isFileBacked) {
            val value = _get_osfhandle(fileDescriptor).toCPointer<COpaque>()
            check(value != INVALID_HANDLE_VALUE) { "Could not get backing file handle for VirtualMemory" }
            value
        }
        else INVALID_HANDLE_VALUE
        val mappingHandle = CreateFileMappingW(
            handle,
            null,
            _accessFlags.toPageProtectionFlags().toUInt(),
            ((_size shr 32) and 0xFFFFFFFF).toUInt(),
            (_size and 0xFFFFFFFF).toUInt(),
            null
        )
        check(mappingHandle != null) { "Could not create (page)file mapping for VirtualMemory" }
        val address = MapViewOfFileEx(
            mappingHandle, _accessFlags.toMappingFlags().toUInt(), 0U, 0U, 0UL, null
        )
        check(address != null) { "Could not obtain mapping address for VirtualMemory" }
        _address = address
        this.mappingHandle = mappingHandle
    }

    override fun unmap() {
        check(UnmapViewOfFile(_address) != 0) { "Could not unmap view for VirtualMemory" }
        check(CloseHandle(mappingHandle) != 0) { "Could not close mapping for VirtualMemory" }
    }

    override fun sync(flags: SyncFlags): Boolean = FlushViewOfFile(_address, _size.convert()) != 0

    override fun lock(): Boolean = VirtualLock(_address, _size.convert()) != 0

    override fun unlock(): Boolean = VirtualUnlock(_address, _size.convert()) != 0

    override fun protect(accessFlags: AccessFlags): Boolean {
        return VirtualProtect(_address, _size.convert(), accessFlags.toPageProtectionFlags().toUInt(), null) != 0
    }
}

actual fun VirtualMemory( // @formatter:off
    size: Long,
    path: Path?,
    accessFlags: AccessFlags,
    mappingFlags: MappingFlags
): VirtualMemory = VirtualMemoryImpl(size, path, accessFlags, mappingFlags) // @formatter:on