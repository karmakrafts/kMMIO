/*
 * Copyright 2025 Karma Krafts
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

/**
 * Get a JNA [Pointer] to the virtual memory at the given [offset].
 *
 * @param offset The offset from the start of the virtual memory.
 * @return A JNA [Pointer] to the virtual memory at the given [offset].
 */
fun VirtualMemory.getPointer(offset: Long = 0L): Pointer = Pointer.createConstant(address + offset)