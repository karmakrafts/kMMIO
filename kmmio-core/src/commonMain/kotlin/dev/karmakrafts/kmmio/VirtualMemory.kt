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

interface VirtualMemory : AutoCloseable {
    companion object {
        const val INVALID_FILE_DESCRIPTOR: Int = -1
    }

    val path: Path?
    val fileDescriptor: Int
    val size: Long
    val accessFlags: AccessFlags
    val mappingFlags: MappingFlags

    val isFileBacked: Boolean
        get() = fileDescriptor != INVALID_FILE_DESCRIPTOR

    fun sync(flags: SyncFlags): Boolean
    fun lock(): Boolean
    fun unlock(): Boolean
    fun protect(accessFlags: AccessFlags): Boolean
    fun resize(size: Long): Boolean
    fun zero()

    fun copyTo(memory: VirtualMemory, size: Long, srcOffset: Long = 0L, dstOffset: Long = 0L): Long
    fun copyFrom(memory: VirtualMemory, size: Long = memory.size, srcOffset: Long = 0L, dstOffset: Long = 0L): Long

    fun readBytes(array: ByteArray, size: Long = this.size, srcOffset: Long = 0L, dstOffset: Long = 0L): Long
    fun writeBytes(array: ByteArray, size: Long = array.size.toLong(), srcOffset: Long = 0L, dstOffset: Long = 0L): Long

    fun readAllBytes(): ByteArray {
        check(size <= Int.MAX_VALUE) { "Memory block size exceeds array limit" }
        return ByteArray(size.toInt()).apply(::readBytes)
    }
}

expect fun VirtualMemory( // @formatter:off
    size: Long,
    path: Path? = null,
    accessFlags: AccessFlags = AccessFlags.READ + AccessFlags.WRITE,
    mappingFlags: MappingFlags = if(path != null) MappingFlags.SHARED else MappingFlags.ANON + MappingFlags.PRIVATE
): VirtualMemory // @formatter:on