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

private class VirtualMemoryImpl(
    initialSize: Long,
    override val path: Path?,
    initialAccessFlags: AccessFlags,
    override val mappingFlags: MappingFlags
) : VirtualMemory {
    private var _size: Long = initialSize
    override val size: Long get() = _size

    private var _accessFlags: AccessFlags = initialAccessFlags
    override val accessFlags: AccessFlags = initialAccessFlags

    override val fileDescriptor: Int
        get() = TODO("Not yet implemented")

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

actual fun VirtualMemory( // @formatter:off
    size: Long,
    path: Path?,
    accessFlags: AccessFlags,
    mappingFlags: MappingFlags
): VirtualMemory = VirtualMemoryImpl(size, path, accessFlags, mappingFlags) // @formatter:on