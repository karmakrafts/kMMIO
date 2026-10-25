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
import kotlin.test.assertEquals

class RandomAccessTest {
    @Test
    fun seekAndTellUsePositionsRelativeToTheRegion() {
        VirtualMemory(64).use { memory ->
            val accessors: List<RandomAccess> = listOf(memory.source(16, 8), memory.sink(16, 8))
            for (access in accessors) {
                assertEquals(0L, access.tell())
                access.seek(5, RandomAccess.Whence.START)
                assertEquals(5L, access.tell())
                access.seek(3)
                assertEquals(8L, access.tell())
                access.seek(-2, RandomAccess.Whence.CURRENT)
                assertEquals(6L, access.tell())
                access.seek(-4, RandomAccess.Whence.END)
                assertEquals(12L, access.tell())
                access.seek(0, RandomAccess.Whence.END)
                assertEquals(16L, access.tell())
                access.seek(0, RandomAccess.Whence.START)
                assertEquals(0L, access.tell())
            }
        }
    }

    @Test
    fun accessorsHaveIndependentPositions() {
        VirtualMemory(16).use { memory ->
            val first: RandomAccess = memory.source()
            val second: RandomAccess = memory.source()
            val sink: RandomAccess = memory.sink()
            first.seek(7)
            sink.seek(3)
            assertEquals(7L, first.tell())
            assertEquals(0L, second.tell())
            assertEquals(3L, sink.tell())
        }
    }
}