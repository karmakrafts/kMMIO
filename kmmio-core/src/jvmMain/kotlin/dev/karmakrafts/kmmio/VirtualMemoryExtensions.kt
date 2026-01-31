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

import java.lang.foreign.MemorySegment

/**
 * Views this virtual memory block as a [MemorySegment].
 *
 * @return a [MemorySegment] viewing this virtual memory block
 */
@Suppress("Since15") // We compile against Panama as a preview feature to be compatible with Java 21
fun VirtualMemory.asSegment(): MemorySegment = MemorySegment.ofAddress(address).reinterpret(size)