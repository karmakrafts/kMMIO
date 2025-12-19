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

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toCPointer

/**
 * Get the native pointer to the start of the virtual memory region.
 *
 * @return The native pointer to the start of the virtual memory region.
 * @throws IllegalStateException If the pointer could not be obtained.
 */
@OptIn(ExperimentalForeignApi::class)
fun VirtualMemory.getPointer(): COpaquePointer = requireNotNull(address.toCPointer()) {
    "Could not obtain pointer to VirtualMemory"
}