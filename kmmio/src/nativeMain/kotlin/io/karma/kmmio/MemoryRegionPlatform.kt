/*
 * Copyright 2025 (C) Karma Krafts & associates
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

package io.karma.kmmio

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * An opaque, type-safe platform-specific handle to a
 * mapped memory region.
 */
interface MemoryRegionHandle {
    /**
     * The base address of the underlying memory mapping.
     */
    @ExperimentalForeignApi
    val address: COpaquePointer
}

/**
 * The current system page size in bytes.
 */
expect val pageSize: Long

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
internal expect fun getLastError(): String?

@OptIn(ExperimentalForeignApi::class)
internal inline fun <reified T> T?.checkLastError(errorCallback: () -> Unit = {}): T {
    if(this == null) {
        errorCallback()
        throw MemoryRegionException(getLastError() ?: "Unknown error")
    }
    return this
}

@OptIn(ExperimentalForeignApi::class)
internal inline fun checkLastError(condition: Boolean, errorCallback: () -> Unit = {}) {
    if (condition) return
    errorCallback()
    throw MemoryRegionException(getLastError() ?: "Unknown error")
}