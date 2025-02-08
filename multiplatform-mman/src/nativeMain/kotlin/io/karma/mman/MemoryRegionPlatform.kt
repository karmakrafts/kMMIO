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

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi

interface MemoryRegionHandle {
    @ExperimentalForeignApi
    val address: COpaquePointer
}

expect val PAGE_SIZE: Long

@ExperimentalForeignApi
internal expect fun mapMemory(
    fd: Int, size: Long, accessFlags: AccessFlags, mappingFlags: MappingFlags
): MemoryRegionHandle?

@ExperimentalForeignApi
internal expect fun unmapMemory(
    handle: MemoryRegionHandle, size: Long
): Boolean

@ExperimentalForeignApi
internal expect fun lockMemory(
    handle: MemoryRegionHandle, size: Long
): Boolean

@ExperimentalForeignApi
internal expect fun unlockMemory(
    handle: MemoryRegionHandle, size: Long
): Boolean

@ExperimentalForeignApi
internal expect fun syncMemory(
    handle: MemoryRegionHandle, size: Long, flags: SyncFlags
): Boolean

@ExperimentalForeignApi
internal expect fun protectMemory(
    handle: MemoryRegionHandle, size: Long, flags: AccessFlags
): Boolean

@ExperimentalForeignApi
internal expect fun getLastError(): String