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

import kotlinx.io.files.Path
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

@PublishedApi
@Suppress("Since15") // We compile against Panama as a preview feature to be compatible with Java
internal class WindowsVirtualMemory( // @formatter:off
    initialSize: Long,
    path: Path?,
    initialAccessFlags: AccessFlags,
    mappingFlags: MappingFlags
) : AbstractVirtualMemory(initialSize, path, initialAccessFlags, mappingFlags) { // @formatter:on
    companion object { // @formatter:off
        private val kernelLookup: SymbolLookup = SymbolLookup.libraryLookup("kernel32", Arena.global())

        private fun getKernelFunction(name: String, descriptor: FunctionDescriptor): MethodHandle {
            val address = kernelLookup.find(name).orElseThrow()
            return Linker.nativeLinker().downcallHandle(address, descriptor)
        }

        private val CreateFileMappingW: MethodHandle = getKernelFunction("CreateFileMappingW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,  // HANDLE hFile
            ValueLayout.ADDRESS,  // LPSECURITY_ATTRIBUTES lpFileMappingAttributes
            ValueLayout.JAVA_INT, // DWORD flProtect
            ValueLayout.JAVA_INT, // DWORD dwMaximumSizeHigh
            ValueLayout.JAVA_INT, // DWORD dwMaximumSizeLow
            ValueLayout.ADDRESS   // LPCWSTR lpName
        ))
        private val MapViewOfFileEx: MethodHandle = getKernelFunction("MapViewOfFileEx", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,   // HANDLE hFileMappingObject
            ValueLayout.JAVA_INT,  // DWORD dwDesiredAccess
            ValueLayout.JAVA_INT,  // DWORD dwFileOffsetHigh
            ValueLayout.JAVA_INT,  // DWORD dwFileOffsetLow
            ValueLayout.JAVA_LONG, // SIZE_T dwNumberOfBytesToMap
            ValueLayout.ADDRESS    // LPVOID lpBaseAddress
        ))
        private val UnmapViewOfFile: MethodHandle = getKernelFunction("UnmapViewOfFile", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS // LPCVOID lpBaseAddress
        ))
        private val FlushViewOfFile: MethodHandle = getKernelFunction("FlushViewOfFile", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // LPCVOID lpBaseAddress
            ValueLayout.JAVA_INT // SIZE_T dwNumberOfBytesToFlush
        ))
        private val VirtualProtect: MethodHandle = getKernelFunction("VirtualProtect", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,   // LPVOID lpAddress
            ValueLayout.JAVA_LONG, // SIZE_T dwSize
            ValueLayout.JAVA_INT,  // DWORD flNewProtect
            ValueLayout.ADDRESS    // PDWORD lpfOldProtect
        ))
        private val VirtualLock: MethodHandle = getKernelFunction("VirtualLock", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,  // LPVOID lpAddress
            ValueLayout.JAVA_LONG // SIZE_T dwSize
        ))
        private val VirtualUnlock: MethodHandle = getKernelFunction("VirtualUnlock", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,  // LPVOID lpAddress
            ValueLayout.JAVA_LONG // SIZE_T dwSize
        ))
        private val CloseHandle: MethodHandle = getKernelFunction("CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS // HANDLE hObject
        ))
        private val _open: MethodHandle = getNativeFunction("_open", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,  // const char* path
            ValueLayout.JAVA_INT, // int oflags
            ValueLayout.JAVA_INT  // int pmode
        ))
        private val _close: MethodHandle = getNativeFunction("_close", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT // int fd
        ))
        private val _chsize_s: MethodHandle = getNativeFunction("_chsize_s", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, // int fd
            ValueLayout.JAVA_LONG // off_t length
        ))
        private val _get_osfhandle: MethodHandle = getNativeFunction("_get_osfhandle", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT // int fd
        ))

        private const val PAGE_NOACCESS: Int = 0x01
        private const val PAGE_READONLY: Int = 0x02
        private const val PAGE_READWRITE: Int = 0x04
        private const val PAGE_EXECUTE_READ: Int = 0x20
        private const val PAGE_EXECUTE_READWRITE: Int = 0x40

        private const val FILE_MAP_READ: Int = 0x04
        private const val FILE_MAP_WRITE: Int = 0x02
        private const val FILE_MAP_EXECUTE: Int = 0x20
        private const val FILE_MAP_ALL_ACCESS: Int = 0x000F001F

        private val INVALID_HANDLE_VALUE: MemorySegment = MemorySegment.ofAddress(-1.toULong().toLong())

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
    } // @formatter:on

    private lateinit var mappingHandle: MemorySegment

    override fun openFile(name: MemorySegment, flags: Int, mask: Int): Int = _open.invokeExact(name, flags, mask) as Int
    override fun closeFile(fd: Int): Int = _close.invokeExact(fd) as Int
    override fun truncateFile(fd: Int, size: Long): Int = _chsize_s.invokeExact(fd, size) as Int

    override fun map() {
        val hiSize = (_size shr 32) and 0xFFFFFFFF
        val loSize = _size and 0xFFFFFFFF
        val handle = if (isFileBacked) {
            val value = _get_osfhandle.invokeExact(fileDescriptor) as MemorySegment
            check(value != INVALID_HANDLE_VALUE) { "Could not get backing file handle for VirtualMemory" }
            value
        }
        else INVALID_HANDLE_VALUE
        val mappingHandle = CreateFileMappingW.invokeExact(
            handle,
            MemorySegment.NULL,
            _accessFlags.toPageProtectionFlags(),
            hiSize.toInt(),
            loSize.toInt(),
            MemorySegment.NULL
        ) as MemorySegment
        check(mappingHandle != MemorySegment.NULL) { "Could not create (page)file mapping for VirtualMemory" }
        val address = MapViewOfFileEx.invokeExact(
            mappingHandle, _accessFlags.toMappingFlags(), 0, 0, 0L, MemorySegment.NULL
        ) as MemorySegment
        check(address != MemorySegment.NULL) { "Could not obtain mapping address for VirtualMemory" }
        _address = address
        this.mappingHandle = mappingHandle
    }

    override fun unmap() {
        check((UnmapViewOfFile.invokeExact(_address) as Int) != 0) { "Could not unmap view for VirtualMemory" }
        check((CloseHandle.invokeExact(mappingHandle) as Int) != 0) { "Could not close mapping for VirtualMemory" }
    }

    override fun sync(flags: SyncFlags): Boolean = (FlushViewOfFile.invokeExact(_address, flags.value.toInt()) as Int) != 0

    override fun lock(): Boolean = (VirtualLock.invokeExact(_address, _size) as Int) != 0

    override fun unlock(): Boolean = (VirtualUnlock.invokeExact(_address, _size) as Int) != 0

    override fun protect(accessFlags: AccessFlags): Boolean = Arena.ofConfined().use { arena ->
        val oldProtection = arena.allocate(ValueLayout.JAVA_INT)
        (VirtualProtect.invokeExact(_address, _size, accessFlags.toPageProtectionFlags(), oldProtection) as Int) != 0
    }
}