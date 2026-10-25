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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.toLong
import kotlin.test.assertEquals

@OptIn(ExperimentalForeignApi::class)
internal actual fun assertPlatformMemoryView(memory: VirtualMemory) {
    memory.zero()
    val pointer = memory.getPointer().reinterpret<ByteVar>()
    assertEquals(memory.address, pointer.toLong())
    memory.writeBytes(byteArrayOf(1, 2, 3))
    assertEquals(1.toByte(), pointer[0])
    assertEquals(3.toByte(), pointer[2])
    pointer[memory.size - 1] = 42
    assertEquals(42.toByte(), memory.readAllBytes().last())
}