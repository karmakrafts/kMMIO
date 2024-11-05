package io.karma.mman

import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.usePinned
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
import platform.posix.memcpy
import platform.posix.mode_t
import platform.posix.open
import kotlin.math.min

/**
 * @author Alexander Hinze
 * @since 29/10/2024
 */

value class SyncFlags private constructor(internal val value: Int) {
    companion object {
        val SYNC: SyncFlags = SyncFlags(1)
        val ASYNC: SyncFlags = SyncFlags(2)
        val INVALIDATE: SyncFlags = SyncFlags(4)
    }

    operator fun plus(flags: SyncFlags): SyncFlags = SyncFlags(value or flags.value)
    operator fun contains(flags: SyncFlags): Boolean = value and flags.value == flags.value
}

value class AccessFlags private constructor(internal val value: Int) {
    companion object {
        val READ: AccessFlags = AccessFlags(1)
        val WRITE: AccessFlags = AccessFlags(2)
        val EXEC: AccessFlags = AccessFlags(4)
    }

    operator fun plus(flags: AccessFlags): AccessFlags = AccessFlags(value or flags.value)
    operator fun contains(flags: AccessFlags): Boolean = value and flags.value == flags.value
}

value class MappingFlags private constructor(internal val value: Int) {
    companion object {
        val ANON: MappingFlags = MappingFlags(1)
        val PRIVATE: MappingFlags = MappingFlags(2)
        val SHARED: MappingFlags = MappingFlags(4)
    }

    operator fun plus(flags: MappingFlags): MappingFlags = MappingFlags(value or flags.value)
    operator fun contains(flags: MappingFlags): Boolean = value and flags.value == flags.value
}

@OptIn(ExperimentalForeignApi::class, InternalMmanApi::class)
class MemoryRegion(baseAddress: COpaquePointer,
                   size: Long,
                   accessFlags: AccessFlags,
                   val mappingFlags: MappingFlags,
                   @property:InternalMmanApi val fd: Int) : AutoCloseable {
    companion object {
        val lastError: String
            get() = getLastError()

        fun map(size: Long, accessFlags: AccessFlags, mappingFlags: MappingFlags = MappingFlags.PRIVATE): MemoryRegion {
            require(size >= 1) { "Memory region size must be larger or equal to 1 byte" }
            val address = mapMemory(-1,
                size,
                accessFlags,
                mappingFlags + MappingFlags.ANON)
            require(isValidAddress(address)) { "Invalid mapping address" }
            return MemoryRegion(address as COpaquePointer, size, accessFlags, (mappingFlags + MappingFlags.ANON), -1)
        }

        @OptIn(UnsafeNumber::class)
        fun map(path: Path,
                accessFlags: AccessFlags,
                mappingFlags: MappingFlags = MappingFlags.SHARED,
                size: Long = PAGE_SIZE,
                override: Boolean = false): MemoryRegion {
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
            if (isWrite && size != fileSize) {
                if (ftruncate(fd, size.convert()) != 0) {
                    close(fd)
                    throw IllegalStateException("Could not truncate file to initial mapping size: ${getLastError()}")
                }
                fileSize = size
            }

            val mappingSize = fileSize ?: size
            val address = mapMemory(fd,
                mappingSize,
                accessFlags,
                mappingFlags)
            require(isValidAddress(address)) { "Invalid mapping address for $path" }
            return MemoryRegion(address as COpaquePointer, mappingSize, accessFlags, mappingFlags, fd)
        }
    }

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

    fun sync(flags: SyncFlags): Boolean = syncMemory(address, size.convert(), flags)

    fun lock(): Boolean = lockMemory(address, size.convert())

    fun unlock(): Boolean = unlockMemory(address, size.convert())

    fun protect(flags: AccessFlags): Boolean {
        if (!protectMemory(address, size.convert(), flags)) return false
        accessFlags = flags
        return true
    }

    fun asSink(bufferSize: Int = 1024): RawSink = MemoryRegionSink(this, bufferSize)

    fun asSource(bufferSize: Int = 1024): RawSource = MemoryRegionSource(this, bufferSize)

    @OptIn(UnsafeNumber::class)
    fun resize(size: Long): Boolean {
        require(!isClosed) { "MemoryRegion has already been disposed" }
        require(unmapMemory(address,
            this.size.convert())) { "Could not unmap memory region for resizing: ${getLastError()}" }
        val result = if (fd != -1) ftruncate(fd, size.convert()) else 0
        address = requireNotNull(mapMemory(fd,
            size.convert(),
            accessFlags,
            mappingFlags)) { "Could not remap memory region" }
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
        require(unmapMemory(address, size.convert())) { "Could not unmap memory region: ${getLastError()}" }
        if (fd != -1) close(fd)
        isClosed = true
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class MemoryRegionSource(
    private val region: MemoryRegion,
    private val bufferSize: Int
) : RawSource {
    private var isClosed: Boolean = false
    private var position: Long = 0

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        require(!region.isClosed) { "Memory region has already been disposed" }
        require(AccessFlags.READ in region.accessFlags || AccessFlags.EXEC in region.accessFlags) { "Cannot read from protected memory" }

        if (isClosed || position == region.size) return -1L

        val buffer = ByteArray(bufferSize)
        val actualByteCount = min(region.size, byteCount)
        var readBytes = 0L

        while (readBytes < actualByteCount) {
            val chunkSize = min(bufferSize.toLong(), actualByteCount - readBytes)
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
internal class MemoryRegionSink(
    private val region: MemoryRegion,
    private val bufferSize: Int
) : RawSink {
    private var isClosed: Boolean = false
    private var position: Long = 0

    override fun write(source: Buffer, byteCount: Long) {
        require(!region.isClosed) { "Memory region has already been disposed" }
        require(AccessFlags.WRITE in region.accessFlags) { "Cannot write to protected memory" }

        if (isClosed) return

        val buffer = ByteArray(bufferSize)
        val actualByteCount = min(source.size, byteCount)
        require(actualByteCount <= region.size) { "Size exceeds memory region bounds" }
        var writtenBytes = 0L

        while (writtenBytes < actualByteCount) {
            var chunkSize = min(bufferSize.toLong(), actualByteCount - writtenBytes)
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