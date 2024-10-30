@file:OptIn(ExperimentalForeignApi::class)

import io.karma.mman.AccessFlags
import io.karma.mman.AccessFlags.Companion
import io.karma.mman.MappingFlags
import io.karma.mman.MemoryRegion
import io.karma.mman.PAGE_SIZE
import io.kotest.assertions.fail
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * @author Alexander Hinze
 * @since 30/10/2024
 */

@Test
fun `Map and unmap private anonymous memory region`() {
    MemoryRegion.map(PAGE_SIZE shl 2, AccessFlags.READ + AccessFlags.WRITE).use {
        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
    }
}

@Test
fun `Map and unmap non-existing file to private mapping`() {
    val path = Path("newfile.txt")
    MemoryRegion.map(path, AccessFlags.READ + AccessFlags.WRITE).use {
        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
    }
    SystemFileSystem.delete(path) // Delete newly created file when test is done
}

@Test
fun `Map and unmap existing file to private mapping`() {
    MemoryRegion.map(Path("testfile.txt"), AccessFlags.READ).use {
        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
    }
}

@Test
fun `Change memory protection flags of private anonymous mapping`() {
    MemoryRegion.map(PAGE_SIZE shl 2, AccessFlags.READ + AccessFlags.WRITE).use {
        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(AccessFlags.READ + AccessFlags.WRITE, it.accessFlags, "Access flags do not match")

        if (!it.protect(AccessFlags.READ)) {
            fail(MemoryRegion.getLastError())
        }

        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(AccessFlags.READ, it.accessFlags, "Access flags do not match")

        if (!it.protect(AccessFlags.READ + Companion.WRITE)) {
            fail(MemoryRegion.getLastError())
        }

        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(AccessFlags.READ + AccessFlags.WRITE, it.accessFlags, "Access flags do not match")
    }
}

@Test
fun `Change memory protection flags of private file mapping`() {
    MemoryRegion.map(Path("testfile.txt"), AccessFlags.READ + Companion.WRITE).use {
        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(AccessFlags.READ + AccessFlags.WRITE, it.accessFlags, "Access flags do not match")

        if (!it.protect(AccessFlags.READ)) {
            fail(MemoryRegion.getLastError())
        }

        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(AccessFlags.READ, it.accessFlags, "Access flags do not match")

        if (!it.protect(AccessFlags.READ + Companion.WRITE)) {
            fail(MemoryRegion.getLastError())
        }

        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(AccessFlags.READ + AccessFlags.WRITE, it.accessFlags, "Access flags do not match")
    }
}

@Test
fun `Resize private anonymous mapping`() {
    MemoryRegion.map(PAGE_SIZE shl 2, AccessFlags.READ + AccessFlags.WRITE).use {
        assertEquals(PAGE_SIZE shl 2, it.size)
        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")

        it.resize(PAGE_SIZE shl 4)

        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(PAGE_SIZE shl 4, it.size)

        it.resize(PAGE_SIZE shl 1)

        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(PAGE_SIZE shl 1, it.size)
    }
}

@Test
fun `Resize private file mapping`() {
    val path = Path("newfile.txt")
    MemoryRegion.map(path, AccessFlags.READ + AccessFlags.WRITE, size = PAGE_SIZE shl 2).use {
        assertEquals(PAGE_SIZE shl 2, it.size)
        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")

        it.resize(PAGE_SIZE shl 4)

        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(PAGE_SIZE shl 4, it.size)

        it.resize(PAGE_SIZE shl 1)

        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        assertEquals(PAGE_SIZE shl 1, it.size)
    }
    SystemFileSystem.delete(path) // Delete newly created file when test is done
}

@Test
fun `Copy data to shared file mapping`() {
    val path = Path("newfile.txt")
    val sourcePath = Path("testfile.txt")
    MemoryRegion.map(path, AccessFlags.READ + AccessFlags.WRITE, MappingFlags.SHARED).use {
        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        it.growIfNeeded(SystemFileSystem.metadataOrNull(sourcePath)?.size ?: PAGE_SIZE)
        it.sink.apply {
            SystemFileSystem.source(sourcePath).buffered().transferTo(this)
            flush()
        }
    }
    assertEquals(SystemFileSystem.source(sourcePath).buffered().readString(),
        SystemFileSystem.source(path).buffered().readString(),
        "File contents do not match")
    SystemFileSystem.delete(path) // Delete newly created file when test is done
}

@Test
fun `Copy data from shared file mapping`() {
    val path = Path("testfile.txt")
    val destPath = Path("newfile.txt")
    MemoryRegion.map(path, AccessFlags.READ, MappingFlags.SHARED).use {
        assertNotEquals(0, it.address.rawValue.toLong(), "Address cannot be 0")
        SystemFileSystem.sink(destPath).buffered().apply {
            transferFrom(it.source)
            flush()
        }
    }
    assertEquals(SystemFileSystem.source(path).buffered().readString(),
        SystemFileSystem.source(destPath).buffered().readString(),
        "File contents do not match")
    SystemFileSystem.delete(destPath) // Delete newly created file when test is done
}