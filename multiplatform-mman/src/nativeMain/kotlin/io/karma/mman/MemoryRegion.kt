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
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.S_IRGRP
import platform.posix.S_IROTH
import platform.posix.S_IRUSR
import platform.posix.S_IWGRP
import platform.posix.S_IWOTH
import platform.posix.S_IWUSR
import platform.posix.S_IXGRP
import platform.posix.S_IXOTH
import platform.posix.S_IXUSR
import platform.posix.close
import platform.posix.ftruncate
import platform.posix.mode_t
import platform.posix.open

/**
 * A mapped block of memory which may be backed by a file.
 */
@OptIn(ExperimentalForeignApi::class, InternalMmanApi::class)
class MemoryRegion private constructor(
    private var handle: MemoryRegionHandle,
    size: Long,
    accessFlags: AccessFlags,
    val mappingFlags: MappingFlags,
    @property:InternalMmanApi val fd: Int
) : AutoCloseable {
    companion object {
        /**
         * Create a new [RawSource] for the given file
         * backed by a [io.karma.mman.MemoryRegion] to rea the file.
         *
         * @param path The path of the file to map into memory for reading.
         * @param bufferSize The size of the buffer used internally by the [RawSource].
         *  By default, this is set to the current system page size.
         * @return A new [RawSource] backed by a [MemoryRegion] instance for reading.
         * @throws MemoryRegionException If the new mapping could not be created.
         */
        fun source(
            path: Path, bufferSize: Int = pageSize.toInt()
        ): RawSource = map(
            path, AccessFlags.READ
        ).asSource(bufferSize)

        /**
         * Create a new [RawSink] for the given file
         * backed by a [io.karma.mman.MemoryRegion] to write the file.
         *
         * @param path The path of the file to map into memory for writing.
         * @param bufferSize The size of the buffer used internally by the [RawSource].
         *  By default, this is set to the current system page size.
         * @return A new [RawSink] backed by a [MemoryRegion] instance for writing.
         * @throws MemoryRegionException If the new mapping could not be created.
         */
        fun sink(
            path: Path, bufferSize: Int = pageSize.toInt()
        ): RawSink = map(
            path, AccessFlags.READ + AccessFlags.WRITE
        ).asSink(bufferSize)

        /**
         * Create a new mapped memory region that is not backed by any file.
         *
         * @param accessFlags The access flags for the newly created memory region. See [AccessFlags].
         * @param mappingFlags The mapping flags for the newly created memory region. See [MappingFlags].
         * @param size The size of the newly created memory region in bytes.
         * @return A new [MemoryRegion] instance with the given flags.
         * @throws MemoryRegionException If the new mapping could not be created.
         */
        fun map(
            size: Long, accessFlags: AccessFlags, mappingFlags: MappingFlags = MappingFlags.PRIVATE
        ): MemoryRegion {
            require(size >= 1) { "Memory region size must be larger or equal to 1 byte" }
            val handle = mapMemory(-1, size, accessFlags, mappingFlags + MappingFlags.ANON).checkLastError()
            return MemoryRegion(handle, size, accessFlags, (mappingFlags + MappingFlags.ANON), -1)
        }

        /**
         * Create a new mapped memory region that is backed by a file.
         *
         * @param path The path to the file to map into memory.
         * @param accessFlags The access flags for the newly created memory region. See [AccessFlags].
         * @param mappingFlags The mapping flags for the newly created memory region. See [MappingFlags].
         * @param size The size of the newly created memory region in bytes.
         * @return A new [MemoryRegion] instance with the given flags.
         * @throws MemoryRegionException If the new mapping could not be created.
         */
        @OptIn(UnsafeNumber::class)
        fun map(
            path: Path,
            accessFlags: AccessFlags,
            mappingFlags: MappingFlags = MappingFlags.SHARED,
            size: Long = pageSize,
            override: Boolean = false
        ): MemoryRegion {
            require(size >= 1) { "Memory region size must be larger or equal to 1 byte" }

            var fileSize = SystemFileSystem.metadataOrNull(path)?.size
            val isWrite = AccessFlags.WRITE in accessFlags

            var openFlags = if (isWrite) O_RDWR else O_RDONLY
            if (SystemFileSystem.exists(path)) {
                if (override) SystemFileSystem.delete(path)
            }
            else if (isWrite) openFlags = openFlags or O_CREAT

            var perms = S_IRUSR or S_IRGRP or S_IROTH
            if (isWrite) perms = perms or S_IWUSR or S_IWGRP or S_IWOTH
            if (AccessFlags.EXEC in accessFlags) perms = perms or S_IXUSR or S_IXGRP or S_IXOTH

            val fd = open(path.toString(), openFlags, perms.convert<mode_t>())
            if (fd == -1) throw MemoryRegionException("Could not open file $path")
            if (isWrite && size != fileSize) {
                checkLastError(ftruncate(fd, size.convert()) == 0) {
                    close(fd)
                }
                fileSize = size
            }

            val mappingSize = fileSize ?: size
            val handle = mapMemory(fd, mappingSize, accessFlags, mappingFlags).checkLastError()
            return MemoryRegion(
                handle, mappingSize, accessFlags, mappingFlags, fd
            )
        }
    }

    internal var isClosed: Boolean = false

    /**
     * The base address of this memory mapping.
     */
    @ExperimentalForeignApi
    val address: COpaquePointer
        get() = handle.address

    /**
     * The unaligned size of this memory region in bytes.
     */
    var size: Long = size
        internal set

    /**
     * The aligned size of this memory region in bytes.
     */
    val alignedSize: Long
        get() = (size + pageSize - 1) and (pageSize - 1).inv()

    /**
     * The access flags that were specified for this memory region.
     * See [AccessFlags].
     */
    var accessFlags: AccessFlags = accessFlags
        internal set

    /**
     * Synchronize this memory region, either flushing all changes
     * to disk if the region is backed by a file or making all changes
     * visible to other processes if the mapping is shared.
     *
     * @param flags The synchronization flags. See [SyncFlags].
     * @return True if the mapping was synchronized successfully.
     */
    fun sync(flags: SyncFlags): Boolean = syncMemory(
        handle, size.convert(), flags
    )

    /**
     * Lock this memory region.
     * This will prevent the memory from being paged into the swap file.
     *
     * @return True if the mapping was locked successfully.
     */
    fun lock(): Boolean = lockMemory(
        handle, size.convert()
    )

    /**
     * Unlock this memory region.
     * This will allow the memory to be paged into the swap file
     * when it was previously locked.
     *
     * @return True if the mapping was unlocked successfully.
     */
    fun unlock(): Boolean = unlockMemory(
        handle, size.convert()
    )

    /**
     * Change the access flags of this memory region.
     *
     * @param flags The new access flags of this memory region. See [AccessFlags].
     * @return True if the access flags were updated successfully.
     */
    fun protect(flags: AccessFlags): Boolean {
        if (!protectMemory(handle, size.convert(), flags)) return false
        accessFlags = flags
        return true
    }

    /**
     * Resize this memory region to the given size.
     *
     * @param size The new size of this memory region in bytes.
     * @return True if the mapping was updated successfully.
     * @throws MemoryRegionException If the region could not be remapped.
     */
    @OptIn(UnsafeNumber::class)
    fun resize(size: Long): Boolean {
        require(!isClosed) { "MemoryRegion has already been disposed" }
        checkLastError(unmapMemory(handle, this.size.convert()))
        if (fd != -1 && ftruncate(fd, size.convert()) != 0) {
            return false
        }
        handle = mapMemory(fd, size.convert(), accessFlags, mappingFlags).checkLastError()
        this.size = size
        return true
    }

    /**
     * If the size of this region is smaller than the specified size,
     * grow this region to fit the new size.
     *
     * @param size The required size of this region in bytes.
     * @return True if this region was resized.
     */
    fun growIfNeeded(size: Long): Boolean {
        if (size <= this.size) return false
        return resize(size)
    }

    /**
     * If the size of this region is larger than the specified size,
     * shrink this region to fit the new size.
     *
     * @param size The required size of this region in bytes.
     * @return True if this region was resized.
     */
    fun shrinkIfNeeded(size: Long): Boolean {
        if (size >= this.size) return false
        return resize(size)
    }

    /**
     * Create a new [RawSink] from this memory region for writing data.
     *
     * @param bufferSize The size of the internal write buffer.
     * @return A new [RawSink] instance for writing to this memory region.
     */
    fun asSink(bufferSize: Int = pageSize.toInt()): RawSink = MemoryRegionSink(
        this, bufferSize
    )

    /**
     * Creates a new [RawSource] from this memory region for reading data.
     *
     * @param bufferSize The size of the internal read buffer.
     * @return A new [RawSource] instance for reading from this memory region.
     */
    fun asSource(bufferSize: Int = pageSize.toInt()): RawSource = MemoryRegionSource(
        this, bufferSize
    )

    override fun close() {
        require(!isClosed) { "Memory region has already been disposed" }
        checkLastError(unmapMemory(handle, size.convert()))
        if (fd != -1) close(fd)
        isClosed = true
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class MemoryRegionSource(
    private val region: MemoryRegion, bufferSize: Int
) : RawMemorySource(
    region.address, region.size, bufferSize
) {
    override fun readAtMostTo(
        sink: Buffer, byteCount: Long
    ): Long {
        require(!region.isClosed) { "Memory region has already been disposed" }
        require(
            AccessFlags.READ in region.accessFlags || AccessFlags.EXEC in region.accessFlags
        ) { "Cannot read from protected memory" }
        return super.readAtMostTo(
            sink, byteCount
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class MemoryRegionSink(
    private val region: MemoryRegion, bufferSize: Int
) : RawMemorySink(region.address, region.size, bufferSize, {
    require(!region.isClosed) { "Memory region has already been disposed" }
    require(region.sync(SyncFlags.SYNC)) { "Memory region could not be synced: ${getLastError()}" }
}) {
    override fun write(
        source: Buffer, byteCount: Long
    ) {
        require(!region.isClosed) { "Memory region has already been disposed" }
        require(AccessFlags.WRITE in region.accessFlags) { "Cannot write to protected memory" }
        super.write(
            source, byteCount
        )
    }
}