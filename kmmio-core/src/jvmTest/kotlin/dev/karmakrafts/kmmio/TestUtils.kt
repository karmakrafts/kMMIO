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

import java.lang.foreign.ValueLayout
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@Suppress("Since15")
internal actual fun assertPlatformMemoryView(memory: VirtualMemory) {
    memory.zero()
    val segment = memory.asSegment()
    assertEquals(memory.address, segment.address())
    assertEquals(memory.size, segment.byteSize())
    memory.writeBytes(byteArrayOf(1, 2, 3))
    assertContentEquals(byteArrayOf(1, 2, 3), segment.asSlice(0, 3).toArray(ValueLayout.JAVA_BYTE))
    segment.set(ValueLayout.JAVA_BYTE, memory.size - 1, 42.toByte())
    assertEquals(42.toByte(), memory.readAllBytes().last())
}