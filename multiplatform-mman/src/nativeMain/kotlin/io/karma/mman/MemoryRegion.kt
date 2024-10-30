package io.karma.mman

import io.karma.mman.MemoryRegion.Companion.BUFFER_SIZE
import io.karma.mman.MemoryRegion.Companion.getLastError
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import mman.MAP_ANON
import mman.MAP_FAILED
import mman.MAP_PRIVATE
import mman.MAP_SHARED
import mman.MS_ASYNC
import mman.MS_INVALIDATE
import mman.MS_SYNC
import mman.PROT_EXEC
import mman.PROT_NONE
import mman.PROT_READ
import mman.PROT_WRITE
import mman.mlock
import mman.mmap
import mman.mprotect
import mman.msync
import mman.munlock
import mman.munmap
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.errno
import platform.posix.ftruncate
import platform.posix.memcpy
import platform.posix.mode_t
import platform.posix.open
import platform.posix.strerror
import kotlin.math.max
import kotlin.math.min

/**
 * @author Alexander Hinze
 * @since 29/10/2024
 */

@OptIn(ExperimentalForeignApi::class)
value class SyncFlags private constructor(internal val value: Int) {
    companion object {
        val NONE: SyncFlags = SyncFlags(0)
        val SYNC: SyncFlags = SyncFlags(MS_SYNC)
        val ASYNC: SyncFlags = SyncFlags(MS_ASYNC)
        val INVALIDATE: SyncFlags = SyncFlags(MS_INVALIDATE)
    }

    operator fun plus(flags: SyncFlags): SyncFlags = SyncFlags(value or flags.value)
    operator fun contains(flags: SyncFlags): Boolean = value and flags.value == flags.value
}

@OptIn(ExperimentalForeignApi::class)
value class AccessFlags private constructor(internal val value: Int) {
    companion object {
        val NONE: AccessFlags = AccessFlags(PROT_NONE)
        val READ: AccessFlags = AccessFlags(PROT_READ)
        val WRITE: AccessFlags = AccessFlags(PROT_WRITE)
        val EXEC: AccessFlags = AccessFlags(PROT_EXEC)
    }

    operator fun plus(flags: AccessFlags): AccessFlags = AccessFlags(value or flags.value)
    operator fun contains(flags: AccessFlags): Boolean = value and flags.value == flags.value
}

@OptIn(ExperimentalForeignApi::class)
value class MappingFlags private constructor(internal val value: Int) {
    companion object {
        val NONE: MappingFlags = MappingFlags(0)
        val ANON: MappingFlags = MappingFlags(MAP_ANON)
        val PRIVATE: MappingFlags = MappingFlags(MAP_PRIVATE)
        val SHARED: MappingFlags = MappingFlags(MAP_SHARED)
    }

    operator fun plus(flags: MappingFlags): MappingFlags = MappingFlags(value or flags.value)
    operator fun contains(flags: MappingFlags): Boolean = value and flags.value == flags.value
}

expect val PAGE_SIZE: Long

@OptIn(ExperimentalForeignApi::class, InternalMmanApi::class)
class MemoryRegion(baseAddress: COpaquePointer,
                   size: Long,
                   accessFlags: AccessFlags,
                   val mappingFlags: MappingFlags,
                   @property:InternalMmanApi val fd: Int) : AutoCloseable {
    companion object {
        internal const val BUFFER_SIZE: Int = 1024

        fun map(size: Long, accessFlags: AccessFlags, mappingFlags: MappingFlags = MappingFlags.PRIVATE): MemoryRegion {
            require(size >= 1) { "MemoryRegion size must be larger or equal to 1 byte" }
            val address = requireNotNull(mmap(null,
                size.convert(),
                accessFlags.value,
                (mappingFlags + MappingFlags.ANON).value,
                -1,
                0)) { "Could not create anonymous memory region" }
            require(address != MAP_FAILED) { "Could not map memory region for resizing: ${getLastError()}" }
            return MemoryRegion(address, size, accessFlags, (mappingFlags + MappingFlags.ANON), -1)
        }

        @OptIn(UnsafeNumber::class)
        fun map(path: Path,
                accessFlags: AccessFlags,
                mappingFlags: MappingFlags = MappingFlags.PRIVATE,
                size: Long = PAGE_SIZE): MemoryRegion {
            require(size >= 1) { "MemoryRegion size must be larger or equal to 1 byte" }

            val fileSize = SystemFileSystem.metadataOrNull(path)?.size
            val actualSize = fileSize?.let { max(it, size) } ?: size
            val isWrite = AccessFlags.WRITE in accessFlags
            val openFlags = if (isWrite) O_RDWR else O_RDONLY
            val perms = when {
                AccessFlags.EXEC in accessFlags -> 0x1C0U // 0700: rwx for owner
                isWrite -> 0x180U                         // 0600: rw for owner
                else -> 0x100U                            // 0400: r for owner
            }
            val fd = open(path.toString(), O_CREAT or openFlags, perms.convert<mode_t>())

            if (isWrite && (actualSize != fileSize) && ftruncate(fd, actualSize.convert()) != 0) {
                close(fd)
                throw IllegalStateException("Could not truncate file to initial mapping size: ${getLastError()}")
            }

            val address = requireNotNull(mmap(null,
                actualSize.convert(),
                accessFlags.value,
                mappingFlags.value,
                fd,
                0)) { "Could not map $path into memory: ${getLastError()}" }
            require(address != MAP_FAILED) { "Could not map $path into memory: ${getLastError()}" }
            return MemoryRegion(address, actualSize, accessFlags, mappingFlags, fd)
        }

        fun getLastError(): String = strerror(errno)?.toKString() ?: "Unknown error"
    }

    val source: RawSource
        get() = MemoryRegionSource(this)

    val sink: RawSink
        get() = MemoryRegionSink(this)

    internal var isClosed: Boolean = false

    // Property wrapper to prevent retrieving dangling pointers
    @ExperimentalForeignApi
    var address: COpaquePointer = baseAddress
        get() {
            require(!isClosed) { "MemoryRegion has already been disposed" }
            return field
        }
        internal set

    var size: Long = size
        internal set

    val alignedSize: Long
        get() = (size + PAGE_SIZE - 1) and (PAGE_SIZE - 1).inv()

    var accessFlags: AccessFlags = accessFlags
        internal set

    fun sync(flags: SyncFlags): Boolean = msync(address, size.convert(), flags.value) == 0

    fun lock(): Boolean = mlock(address, size.convert()) == 0

    fun unlock(): Boolean = munlock(address, size.convert()) == 0

    fun protect(flags: AccessFlags): Boolean {
        if (mprotect(address, size.convert(), flags.value) != 0) return false
        accessFlags = flags
        return true
    }

    @OptIn(UnsafeNumber::class)
    fun resize(size: Long): Boolean {
        require(!isClosed) { "MemoryRegion has already been disposed" }
        require(munmap(address,
            this.size.convert()) == 0) { "Could not unmap memory region for resizing: ${getLastError()}" }
        val result = if (fd != -1) ftruncate(fd, size.convert()) else 0
        address = requireNotNull(mmap(null,
            size.convert(),
            accessFlags.value,
            mappingFlags.value,
            fd,
            0)) { "Could not map memory region for resizing: ${getLastError()}" }
        require(address != MAP_FAILED) { "Could not map memory region for resizing: ${getLastError()}" }
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

    override fun close() {
        require(!isClosed) { "Memory region has already been disposed" }
        require(munmap(address, size.convert()) == 0) { "Could not unmap memory region: ${getLastError()}" }
        if (fd != -1) close(fd)
        isClosed = true
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class MemoryRegionSource(private val region: MemoryRegion) : RawSource {
    private var isClosed: Boolean = false
    private var position: Long = 0

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        require(!region.isClosed) { "Memory region has already been disposed" }
        require(AccessFlags.READ in region.accessFlags || AccessFlags.EXEC in region.accessFlags) { "Cannot read from protected memory" }

        if (isClosed || position == region.size) return -1L

        val buffer = ByteArray(BUFFER_SIZE)
        val actualByteCount = min(region.size, byteCount)
        var readBytes = 0L

        while (readBytes < actualByteCount) {
            val chunkSize = min(BUFFER_SIZE.toLong(), actualByteCount - readBytes)
            buffer.usePinned {
                memcpy(it.addressOf(0),
                    interpretCPointer<COpaque>(region.address.rawValue + position + readBytes),
                    chunkSize.convert())
            }
            sink.write(buffer, endIndex = chunkSize.toInt())
            readBytes += chunkSize
        }

        position += readBytes
        return readBytes
    }

    override fun close() {
        isClosed = true
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class MemoryRegionSink(private val region: MemoryRegion) : RawSink {
    private var isClosed: Boolean = false
    private var position: Long = 0

    override fun write(source: Buffer, byteCount: Long) {
        require(!region.isClosed) { "Memory region has already been disposed" }
        require(AccessFlags.WRITE in region.accessFlags) { "Cannot write to protected memory" }

        if (isClosed) return

        val buffer = ByteArray(BUFFER_SIZE)
        val actualByteCount = min(source.size, byteCount)
        require(actualByteCount <= region.size) { "Size exceeds memory region bounds" }
        var writtenBytes = 0L

        while (writtenBytes < actualByteCount) {
            var chunkSize = min(BUFFER_SIZE.toLong(), actualByteCount - writtenBytes)
            chunkSize = source.readAtMostTo(buffer, endIndex = chunkSize.toInt()).toLong()
            if (chunkSize == -1L) break // EOF
            buffer.usePinned {
                memcpy(interpretCPointer<COpaque>(region.address.rawValue + position + writtenBytes),
                    it.addressOf(0),
                    chunkSize.convert())
            }
            writtenBytes += chunkSize
        }

        position += writtenBytes
    }

    override fun flush() {
        require(!region.isClosed) { "Memory region has already been disposed" }
        require(region.sync(SyncFlags.SYNC)) { "Memory region could not be synced: ${getLastError()}" }
    }

    override fun close() {
        isClosed = true
    }
}