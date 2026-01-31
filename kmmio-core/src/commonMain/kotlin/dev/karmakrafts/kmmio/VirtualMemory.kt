/*
 * Copyright 2026 Karma Krafts
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

/**
 * Represents a block of virtual memory.
 *
 * This may be backed by a file or by anonymous memory.
 */
interface VirtualMemory : AutoCloseable {
    companion object {
        /**
         * Constant representing an invalid file descriptor.
         */
        const val INVALID_FILE_DESCRIPTOR: Int = -1
    }

    /**
     * The start address of the virtual memory block.
     */
    val address: Long

    /**
     * The path to the file backing this virtual memory block, or `null` if it is anonymous.
     */
    val path: Path?

    /**
     * The file descriptor for the file backing this virtual memory block,
     * or [INVALID_FILE_DESCRIPTOR] if it is anonymous.
     */
    val fileDescriptor: Int

    /**
     * The size of the virtual memory block in bytes.
     */
    val size: Long

    /**
     * The access permissions for this virtual memory block.
     */
    val accessFlags: AccessFlags

    /**
     * The mapping type for this virtual memory block.
     */
    val mappingFlags: MappingFlags

    /**
     * Whether this virtual memory block is backed by a file.
     */
    val isFileBacked: Boolean get() = path != null

    /**
     * Synchronizes the changes made to the virtual memory block with the underlying file.
     *
     * @param flags the synchronization flags
     * @return `true` if the synchronization was successful, `false` otherwise
     */
    fun sync(flags: SyncFlags): Boolean

    /**
     * Locks the virtual memory block into RAM, preventing it from being swapped out.
     *
     * @return `true` if the lock was successful, `false` otherwise
     */
    fun lock(): Boolean

    /**
     * Unlocks the virtual memory block from RAM.
     *
     * @return `true` if the unlock was successful, `false` otherwise
     */
    fun unlock(): Boolean

    /**
     * Changes the access permissions for this virtual memory block.
     *
     * @param accessFlags the new access permissions
     * @return `true` if the change was successful, `false` otherwise
     */
    fun protect(accessFlags: AccessFlags): Boolean

    /**
     * Resizes the virtual memory block.
     *
     * @param size the new size in bytes
     * @return `true` if the resize was successful, `false` otherwise
     */
    fun resize(size: Long): Boolean

    /**
     * Fills the virtual memory block with zeros.
     */
    fun zero()

    /**
     * Copies data from this virtual memory block to another virtual memory block.
     *
     * @param memory the destination virtual memory block
     * @param size the number of bytes to copy
     * @param srcOffset the offset in this virtual memory block to start copying from
     * @param dstOffset the offset in the destination virtual memory block to start copying to
     * @return the number of bytes actually copied
     */
    fun copyTo(memory: VirtualMemory, size: Long, srcOffset: Long = 0L, dstOffset: Long = 0L): Long

    /**
     * Copies data from another virtual memory block to this virtual memory block.
     *
     * @param memory the source virtual memory block
     * @param size the number of bytes to copy
     * @param srcOffset the offset in the source virtual memory block to start copying from
     * @param dstOffset the offset in this virtual memory block to start copying to
     * @return the number of bytes actually copied
     */
    fun copyFrom(memory: VirtualMemory, size: Long = memory.size, srcOffset: Long = 0L, dstOffset: Long = 0L): Long

    /**
     * Reads bytes from this virtual memory block into a byte array.
     *
     * @param array the destination byte array
     * @param size the number of bytes to read
     * @param srcOffset the offset in this virtual memory block to start reading from
     * @param dstOffset the offset in the destination byte array to start writing to
     * @return the number of bytes actually read
     */
    fun readBytes(array: ByteArray, size: Long = this.size, srcOffset: Long = 0L, dstOffset: Long = 0L): Long

    /**
     * Writes bytes from a byte array into this virtual memory block.
     *
     * @param array the source byte array
     * @param size the number of bytes to write
     * @param srcOffset the offset in the source byte array to start reading from
     * @param dstOffset the offset in this virtual memory block to start writing to
     * @return the number of bytes actually written
     */
    fun writeBytes(array: ByteArray, size: Long = array.size.toLong(), srcOffset: Long = 0L, dstOffset: Long = 0L): Long

    /**
     * Reads all bytes from this virtual memory block into a new byte array.
     *
     * @return the byte array containing all bytes from this virtual memory block
     * @throws IllegalStateException if the size of the virtual memory block exceeds [Int.MAX_VALUE]
     */
    fun readAllBytes(): ByteArray {
        check(size <= Int.MAX_VALUE) { "Memory block size exceeds array limit" }
        return ByteArray(size.toInt()).apply(::readBytes)
    }
}

/**
 * Creates a new [VirtualMemory] instance.
 *
 * @param size the size of the virtual memory block in bytes
 * @param path the path to the file to map, or `null` for anonymous memory
 * @param accessFlags the access permissions for the virtual memory block
 * @param mappingFlags the mapping type for the virtual memory block
 * @return the new [VirtualMemory] instance
 */
expect fun VirtualMemory( // @formatter:off
    size: Long,
    path: Path? = null,
    accessFlags: AccessFlags = AccessFlags.READ + AccessFlags.WRITE,
    mappingFlags: MappingFlags = if(path != null) MappingFlags.SHARED else MappingFlags.ANON + MappingFlags.PRIVATE
): VirtualMemory // @formatter:on