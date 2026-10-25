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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NativeUtilsTest {
    @Test
    fun allocateEmptyCStr() {
        "".allocateCStr().use { memory ->
            assertEquals(1L, memory.size())
            assertEquals(0.toByte(), memory.getByte(0L))
        }
    }

    @Test
    fun allocateCStr() {
        val data = "testfile.bin".encodeToByteArray() + byteArrayOf(0)
        "testfile.bin".allocateCStr().use { memory ->
            assertEquals(data.size.toLong(), memory.size())
            assertContentEquals(data, memory.getByteArray(0L, data.size))
        }
    }

    @Test
    fun allocateUtf8CStr() {
        val value = "kmmio-\u00e4-\u4e16\u754c.bin"
        val data = value.encodeToByteArray() + byteArrayOf(0)
        value.allocateCStr().use { memory ->
            assertEquals(data.size.toLong(), memory.size())
            assertContentEquals(data, memory.getByteArray(0L, data.size))
        }
    }
}