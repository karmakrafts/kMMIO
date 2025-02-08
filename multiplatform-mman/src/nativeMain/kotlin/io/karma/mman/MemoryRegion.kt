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

value class SyncFlags private constructor(private val value: Int) {
    companion object {
        val SYNC: SyncFlags = SyncFlags(1)
        val ASYNC: SyncFlags = SyncFlags(2)
        val INVALIDATE: SyncFlags = SyncFlags(4)
    }

    operator fun plus(flags: SyncFlags): SyncFlags = SyncFlags(value or flags.value)
    operator fun contains(flags: SyncFlags): Boolean = value and flags.value == flags.value
}

value class AccessFlags private constructor(private val value: Int) {
    companion object {
        val READ: AccessFlags = AccessFlags(1)
        val WRITE: AccessFlags = AccessFlags(2)
        val EXEC: AccessFlags = AccessFlags(4)
    }

    operator fun plus(flags: AccessFlags): AccessFlags = AccessFlags(value or flags.value)
    operator fun contains(flags: AccessFlags): Boolean = value and flags.value == flags.value
}

value class MappingFlags private constructor(private val value: Int) {
    companion object {
        val ANON: MappingFlags = MappingFlags(1)
        val PRIVATE: MappingFlags = MappingFlags(2)
        val SHARED: MappingFlags = MappingFlags(4)
    }

    operator fun plus(flags: MappingFlags): MappingFlags = MappingFlags(value or flags.value)
    operator fun contains(flags: MappingFlags): Boolean = value and flags.value == flags.value
}

@OptIn(
    ExperimentalForeignApi::class, InternalMmanApi::class
)
class MemoryRegion(
    private var handle: MemoryRegionHandle,
    size: Long,
    accessFlags: AccessFlags,
    val mappingFlags: MappingFlags,
    @property:InternalMmanApi val fd: Int
) : AutoCloseable {
    companion object {
        val lastError: String
            get() = getLastError()

        fun source(
            path: Path, bufferSize: Int = PAGE_SIZE.toInt()
        ): RawSource = map(
            path, AccessFlags.READ
        ).asSource(bufferSize)

        fun sink(
            path: Path, bufferSize: Int = PAGE_SIZE.toInt()
        ): RawSink = map(
            path, AccessFlags.READ + AccessFlags.WRITE
        ).asSink(bufferSize)

        fun map(
            size: Long, accessFlags: AccessFlags, mappingFlags: MappingFlags = MappingFlags.PRIVATE
        ): MemoryRegion {
            require(size >= 1) { "Memory region size must be larger or equal to 1 byte" }
            val handle = requireNotNull(
                mapMemory(
                    -1, size, accessFlags, mappingFlags + MappingFlags.ANON
                )
            ) { "Could not create anonymous memory mapping" }
            return MemoryRegion(
                handle, size, accessFlags, (mappingFlags + MappingFlags.ANON), -1
            )
        }

        @OptIn(UnsafeNumber::class)
        fun map(
            path: Path,
            accessFlags: AccessFlags,
            mappingFlags: MappingFlags = MappingFlags.SHARED,
            size: Long = PAGE_SIZE,
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

            val fd = open(
                path.toString(), openFlags, perms.convert<mode_t>()
            )
            if (isWrite && size != fileSize) {
                if (ftruncate(
                        fd, size.convert()
                    ) != 0
                ) {
                    close(fd)
                    throw IllegalStateException("Could not truncate file to initial mapping size: ${getLastError()}")
                }
                fileSize = size
            }

            val mappingSize = fileSize ?: size
            val handle = requireNotNull(
                mapMemory(
                    fd, mappingSize, accessFlags, mappingFlags
                )
            ) { "Could not create file memory mapping for $path" }
            return MemoryRegion(
                handle, mappingSize, accessFlags, mappingFlags, fd
            )
        }
    }

    internal var isClosed: Boolean = false

    @ExperimentalForeignApi
    val address: COpaquePointer
        get() = handle.address

    var size: Long = size
        internal set

    val alignedSize: Long
        get() = (size + PAGE_SIZE - 1) and (PAGE_SIZE - 1).inv()

    var accessFlags: AccessFlags = accessFlags
        internal set

    fun sync(flags: SyncFlags): Boolean = syncMemory(
        handle, size.convert(), flags
    )

    fun lock(): Boolean = lockMemory(
        handle, size.convert()
    )

    fun unlock(): Boolean = unlockMemory(
        handle, size.convert()
    )

    fun protect(flags: AccessFlags): Boolean {
        if (!protectMemory(
                handle, size.convert(), flags
            )
        ) return false
        accessFlags = flags
        return true
    }

    @OptIn(UnsafeNumber::class)
    fun resize(size: Long): Boolean {
        require(!isClosed) { "MemoryRegion has already been disposed" }
        require(
            unmapMemory(
                handle, this.size.convert()
            )
        ) { "Could not unmap memory region for resizing: ${getLastError()}" }
        val result = if (fd != -1) ftruncate(
            fd, size.convert()
        )
        else 0
        handle = requireNotNull(
            mapMemory(
                fd, size.convert(), accessFlags, mappingFlags
            )
        ) { "Could not remap memory region" }
        this.size = size
        return result == 0
    }

    fun growIfNeeded(size: Long): Boolean {
        if (size <= this.size) return false
        return resize(size)
    }

    fun shrinkIfNeeded(size: Long): Boolean {
        if (size >= this.size) return false
        return resize(size)
    }

    fun asSink(bufferSize: Int = PAGE_SIZE.toInt()): RawSink = MemoryRegionSink(
        this, bufferSize
    )

    fun asSource(bufferSize: Int = PAGE_SIZE.toInt()): RawSource = MemoryRegionSource(
        this, bufferSize
    )

    override fun close() {
        require(!isClosed) { "Memory region has already been disposed" }
        require(
            unmapMemory(
                handle, size.convert()
            )
        ) { "Could not unmap memory region: ${getLastError()}" }
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