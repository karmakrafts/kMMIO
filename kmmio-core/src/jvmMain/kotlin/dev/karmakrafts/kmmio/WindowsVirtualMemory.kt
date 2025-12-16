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
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.StructLayout
import java.lang.foreign.UnionLayout
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

@PublishedApi
@Suppress("Since15") // We compile against Panama as a preview feature to be compatible with Java
internal class WindowsVirtualMemory(
    initialSize: Long,
    override val path: Path?,
    initialAccessFlags: AccessFlags,
    override val mappingFlags: MappingFlags
) : VirtualMemory {
    companion object { // @formatter:off
        private val SYSTEM_INFO_S: StructLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_SHORT, // WORD wProcessorArchitecture
            ValueLayout.JAVA_SHORT  // WORD wReserved
        )
        private val SYSTEM_INFO_U: UnionLayout = MemoryLayout.unionLayout(
            ValueLayout.JAVA_INT, // DWORD dwOemId
            SYSTEM_INFO_S         // DUMMYSTRUCTNAME
        )
        private val SYSTEM_INFO: StructLayout = MemoryLayout.structLayout(
            SYSTEM_INFO_U,          // DUMMYUNIONNAME
            ValueLayout.JAVA_INT,   // DWORD dwPageSize
            ValueLayout.ADDRESS,    // LPVOID lpMinimumApplicationAddress
            ValueLayout.ADDRESS,    // LPVOID lpMaximumApplicationAddress
            ValueLayout.ADDRESS,    // DWORD_PTR dwActiveProcessorMask
            ValueLayout.JAVA_INT,   // DWORD dwNumberOfProcessors
            ValueLayout.JAVA_INT,   // DWORD dwProcessorType
            ValueLayout.JAVA_INT,   // DWORD dwAllocationGranularity
            ValueLayout.JAVA_SHORT, // WORD wProcessorLevel
            ValueLayout.JAVA_SHORT  // WORD wProcessorRevision
        )
        private val SECURITY_DESCRIPTOR: StructLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_BYTE,  // BYTE Revision
            ValueLayout.JAVA_BYTE,  // BYTE Sbz1
            ValueLayout.JAVA_SHORT, // SECURITY_DESCRIPTOR_CONTROL Control
            ValueLayout.ADDRESS,    // PSID Owner
            ValueLayout.ADDRESS,    // PSID Group
            ValueLayout.ADDRESS,    // PACL Sacl
            ValueLayout.ADDRESS     // PACL Dacl
        )

        private val CreateFileMappingW: MethodHandle = getNativeFunction("CreateFileMappingW", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,  // HANDLE hFile
            ValueLayout.ADDRESS,  // LPSECURITY_ATTRIBUTES lpFileMappingAttributes
            ValueLayout.JAVA_INT, // DWORD flProtect
            ValueLayout.JAVA_INT, // DWORD dwMaximumSizeHigh
            ValueLayout.JAVA_INT, // DWORD dwMaximumSizeLow
            ValueLayout.ADDRESS   // LPCWSTR lpName
        ))
        private val MapViewOfFileEx: MethodHandle = getNativeFunction("MapViewOfFileEx", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,   // HANDLE hFileMappingObject
            ValueLayout.JAVA_INT,  // DWORD dwDesiredAccess
            ValueLayout.JAVA_INT,  // DWORD dwFileOffsetHigh
            ValueLayout.JAVA_INT,  // DWORD dwFileOffsetLow
            ValueLayout.JAVA_LONG, // SIZE_T dwNumberOfBytesToMap
            ValueLayout.ADDRESS    // LPVOID lpBaseAddress
        ))
        private val UnmapViewOfFile: MethodHandle = getNativeFunction("UnmapViewOfFile", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS // LPCVOID lpBaseAddress
        ))
        private val FlushViewOfFile: MethodHandle = getNativeFunction("FlushViewOfFile", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, // LPCVOID lpBaseAddress
            ValueLayout.JAVA_INT // SIZE_T dwNumberOfBytesToFlush
        ))
        private val VirtualProtect: MethodHandle = getNativeFunction("VirtualProtect", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,   // LPVOID lpAddress
            ValueLayout.JAVA_LONG, // SIZE_T dwSize
            ValueLayout.JAVA_INT,  // DWORD flNewProtect
            ValueLayout.ADDRESS    // PDWORD lpfOldProtect
        ))
        private val VirtualLock: MethodHandle = getNativeFunction("VirtualLock", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,  // LPVOID lpAddress
            ValueLayout.JAVA_LONG // SIZE_T dwSize
        ))
        private val VirtualUnlock: MethodHandle = getNativeFunction("VirtualUnlock", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,  // LPVOID lpAddress
            ValueLayout.JAVA_LONG // SIZE_T dwSize
        ))
        private val CloseHandle: MethodHandle = getNativeFunction("CloseHandle", FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS // HANDLE hObject
        ))
        private val InitializeSecurityDescriptor: MethodHandle = getNativeFunction("InitializeSecurityDescriptor",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS, // PSECURITY_DESCRIPTOR pSecurityDesciptor
                ValueLayout.JAVA_INT // DWORD dwRevision
            ))
        private val GetSystemInfo: MethodHandle = getNativeFunction("GetSystemInfo", FunctionDescriptor.ofVoid(
            ValueLayout.ADDRESS // LPSYSTEM_INFO lpSystemInfo
        ))
        private val _get_osfhandle: MethodHandle = getNativeFunction("_get_osfhandle", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT // int fd
        ))
    } // @formatter:on

    private var _size: Long = initialSize
    override val size: Long get() = _size

    private var _accessFlags: AccessFlags = initialAccessFlags
    override val accessFlags: AccessFlags = initialAccessFlags

    override val fileDescriptor: Int
        get() = TODO("Not yet implemented")

    override val address: Long
        get() = TODO("Not yet implemented")

    override fun zero() {
        TODO("Not yet implemented")
    }

    override fun sync(flags: SyncFlags): Boolean {
        TODO("Not yet implemented")
    }

    override fun lock(): Boolean {
        TODO("Not yet implemented")
    }

    override fun unlock(): Boolean {
        TODO("Not yet implemented")
    }

    override fun protect(accessFlags: AccessFlags): Boolean {
        TODO("Not yet implemented")
    }

    override fun resize(size: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun copyTo(
        memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long
    ): Long {
        TODO("Not yet implemented")
    }

    override fun copyFrom(
        memory: VirtualMemory, size: Long, srcOffset: Long, dstOffset: Long
    ): Long {
        TODO("Not yet implemented")
    }

    override fun readBytes(
        array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long
    ): Long {
        TODO("Not yet implemented")
    }

    override fun writeBytes(
        array: ByteArray, size: Long, srcOffset: Long, dstOffset: Long
    ): Long {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}