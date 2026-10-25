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

import com.sun.jna.Pointer
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

internal actual fun assertPlatformMemoryView(memory: VirtualMemory) {
    memory.zero()
    val pointer = memory.getPointer()
    assertEquals(memory.address, Pointer.nativeValue(pointer))
    val offsetPointer = memory.getPointer(2)
    assertEquals(memory.address + 2, Pointer.nativeValue(offsetPointer))
    memory.writeBytes(byteArrayOf(1, 2, 3))
    assertContentEquals(byteArrayOf(1, 2, 3), pointer.getByteArray(0, 3))
    offsetPointer.setByte(0, 42)
    assertEquals(42.toByte(), memory.readAllBytes()[2])
}