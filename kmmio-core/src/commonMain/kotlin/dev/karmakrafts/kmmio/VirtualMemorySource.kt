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

import kotlinx.io.Buffer
import kotlinx.io.RawSource

private class VirtualMemorySource(
    private val memory: VirtualMemory, private val size: Long, private val offset: Long
) : RawSource {
    override fun close() {
        TODO("Not yet implemented")
    }

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        TODO("Not yet implemented")
    }
}

fun VirtualMemory.source(size: Long = this.size, offset: Long = 0L): RawSource = VirtualMemorySource(this, size, offset)