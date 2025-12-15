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
import kotlinx.io.RawSink

private class VirtualMemorySink(
    private val memory: VirtualMemory
) : RawSink {
    override fun close() {
        TODO("Not yet implemented")
    }

    override fun flush() {
        TODO("Not yet implemented")
    }

    override fun write(source: Buffer, byteCount: Long) {
        TODO("Not yet implemented")
    }
}

fun VirtualMemory.sink(size: Long = this.size, offset: Long = 0L): RawSink = VirtualMemorySink(this)