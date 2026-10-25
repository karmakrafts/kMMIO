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

@PublishedApi
internal expect val isSharedMemoryAvailable: Boolean

/**
 * Represents a block of virtual memory.
 *
 * This may be backed by a file or by anonymous memory.
 */
public interface VirtualMemory : AutoCloseable {
    public companion object {
        /**
         * Constant representing an invalid file descriptor.
         */
        public const val INVALID_FILE_DESCRIPTOR: Int = -1

        /**
         * Whether shared memory is available in current environment.
         * May be false on more restrictive systems like Android and iOS (and its derivatives).
         *
         * If this is `false` and `VirtualMemory` with the `MappingFlags.SHARED` is created,
         * the instantiation may cause an exception.
         */
        public inline val isSharedAvailable: Boolean
            get() = isSharedMemoryAvailable
    }

    /**
     * The start address of the virtual memory block.
     */
    public val address: Long

    /**
     * The path to the file backing this virtual memory block, or `null` if it is anonymous.
     */
    public val path: Path?

    /**
     * The file descriptor for the file backing this virtual memory block,
     * or [INVALID_FILE_DESCRIPTOR] if it is anonymous.
     */
    public val fileDescriptor: Int

    /**
     * The size of the virtual memory block in bytes.
     */
    public val size: Long

    /**
     * The access permissions for this virtual memory block.
     */
    public val accessFlags: AccessFlags

    /**
     * The mapping type for this virtual memory block.
     */
    public val mappingFlags: MappingFlags

    /**
     * Whether this virtual memory block is backed by a file.
     */
    public val isFileBacked: Boolean get() = path != null

    /**
     * Synchronizes the changes made to the virtual memory block with the underlying file.
     *
     * @param flags the synchronization flags
     * @return `true` if the synchronization was successful, `false` otherwise
     */
    public fun sync(flags: SyncFlags): Boolean

    /**
     * Locks the virtual memory block into RAM, preventing it from being swapped out.
     *
     * @return `true` if the lock was successful, `false` otherwise
     */
    public fun lock(): Boolean

    /**
     * Unlocks the virtual memory block from RAM.
     *
     * @return `true` if the unlock was successful, `false` otherwise
     */
    public fun unlock(): Boolean

    /**
     * Changes the access permissions for this virtual memory block.
     *
     * @param accessFlags the new access permissions
     * @return `true` if the change was successful, `false` otherwise
     */
    public fun protect(accessFlags: AccessFlags): Boolean

    /**
     * Resizes the virtual memory block.
     *
     * @param size the new size in bytes
     * @return `true` if the resize was successful, `false` otherwise
     */
    public fun resize(size: Long): Boolean

    /**
     * Fills the virtual memory block with zeros.
     */
    public fun zero()

    /**
     * Copies data from this virtual memory block to another virtual memory block.
     *
     * @param memory the destination virtual memory block
     * @param size the number of bytes to copy
     * @param srcOffset the offset in this virtual memory block to start copying from
     * @param dstOffset the offset in the destination virtual memory block to start copying to
     * @return the number of bytes actually copied
     */
    public fun copyTo(memory: VirtualMemory, size: Long, srcOffset: Long = 0L, dstOffset: Long = 0L): Long

    /**
     * Copies data from another virtual memory block to this virtual memory block.
     *
     * @param memory the source virtual memory block
     * @param size the number of bytes to copy
     * @param srcOffset the offset in the source virtual memory block to start copying from
     * @param dstOffset the offset in this virtual memory block to start copying to
     * @return the number of bytes actually copied
     */
    public fun copyFrom(
        memory: VirtualMemory,
        size: Long = memory.size,
        srcOffset: Long = 0L,
        dstOffset: Long = 0L
    ): Long

    /**
     * Reads bytes from this virtual memory block into a byte array.
     *
     * @param array the destination byte array
     * @param size the number of bytes to read
     * @param srcOffset the offset in this virtual memory block to start reading from
     * @param dstOffset the offset in the destination byte array to start writing to
     * @return the number of bytes actually read
     */
    public fun readBytes(array: ByteArray, size: Long = this.size, srcOffset: Long = 0L, dstOffset: Long = 0L): Long

    /**
     * Writes bytes from a byte array into this virtual memory block.
     *
     * @param array the source byte array
     * @param size the number of bytes to write
     * @param srcOffset the offset in the source byte array to start reading from
     * @param dstOffset the offset in this virtual memory block to start writing to
     * @return the number of bytes actually written
     */
    public fun writeBytes(
        array: ByteArray,
        size: Long = array.size.toLong(),
        srcOffset: Long = 0L,
        dstOffset: Long = 0L
    ): Long

    /**
     * Reads all bytes from this virtual memory block into a new byte array.
     *
     * @return the byte array containing all bytes from this virtual memory block
     * @throws IllegalStateException if the size of the virtual memory block exceeds [Int.MAX_VALUE]
     */
    public fun readAllBytes(): ByteArray {
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
public expect fun VirtualMemory( // @formatter:off
    size: Long,
    path: Path? = null,
    accessFlags: AccessFlags = AccessFlags.READ + AccessFlags.WRITE,
    mappingFlags: MappingFlags = if(path != null) MappingFlags.SHARED else MappingFlags.ANON + MappingFlags.PRIVATE
): VirtualMemory // @formatter:on