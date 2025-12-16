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
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup

@Suppress("Since15") // We compile against Panama as a preview feature to be compatible with Java
internal class WindowsVirtualMemory(
    initialSize: Long,
    override val path: Path?,
    initialAccessFlags: AccessFlags,
    override val mappingFlags: MappingFlags
) : VirtualMemory {
    companion object {
        private val symbolLookup: SymbolLookup = Linker.nativeLinker().defaultLookup()
        private val CreateFileMappingW: MemorySegment = symbolLookup.find("CreateFileMappingW").orElseThrow()
        private val MapViewOfFileEx: MemorySegment = symbolLookup.find("MapViewOfFileEx").orElseThrow()
        private val UnmapViewOfFile: MemorySegment = symbolLookup.find("UnmapViewOfFile").orElseThrow()
        private val FlushViewOfFile: MemorySegment = symbolLookup.find("FlushViewOfFile").orElseThrow()
        private val VirtualProtect: MemorySegment = symbolLookup.find("VirtualProtect").orElseThrow()
        private val VirtualLock: MemorySegment = symbolLookup.find("VirtualLock").orElseThrow()
        private val VirtualUnlock: MemorySegment = symbolLookup.find("VirtualUnlock").orElseThrow()
        private val CloseHandle: MemorySegment = symbolLookup.find("CloseHandle").orElseThrow()
        private val InitializeSecurityDescriptor: MemorySegment =
            symbolLookup.find("InitializeSecurityDescriptor").orElseThrow()
        private val _get_osfhandle: MemorySegment = symbolLookup.find("_get_osfhandle").orElseThrow()
    }

    private var _size: Long = initialSize
    override val size: Long get() = _size

    private var _accessFlags: AccessFlags = initialAccessFlags
    override val accessFlags: AccessFlags = initialAccessFlags

    override val fileDescriptor: Int
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