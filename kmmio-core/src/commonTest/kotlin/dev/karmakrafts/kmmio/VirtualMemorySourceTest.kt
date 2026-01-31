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

import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VirtualMemorySourceTest {
    @Test
    fun `Read via source from memory`() {
        val data = "Hello, source!".encodeToByteArray()
        val memory = VirtualMemory(1024)

        // Preload memory with data
        memory.writeBytes(data, size = data.size.toLong(), srcOffset = 0L, dstOffset = 0L)

        val source = memory.source(size = data.size.toLong(), offset = 0L)
        val buffer = Buffer()

        val read = source.readAtMostTo(buffer, data.size.toLong())
        assertEquals(data.size.toLong(), read)

        val out = ByteArray(data.size)
        val copied = buffer.readAtMostTo(out, 0, out.size)
        assertEquals(out.size, copied)

        assertContentEquals(data.asList(), out.asList())

        memory.close()
    }
}
