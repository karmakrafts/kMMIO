/*
 * Copyright 2025 (C) Karma Krafts & associates
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

package io.karma.kmmio

import io.karma.kmmio.MappingFlags.Companion.SHARED


/**
 * The type of mapping created for a [MemoryRegion].
 */
value class MappingFlags private constructor(private val value: Int) {
    companion object {
        /**
         * A mapping which is not backed by any file.
         * May be used in conjunction with [SHARED].
         */
        val ANON: MappingFlags = MappingFlags(1)

        /**
         * A mapping which is effectively copy-on-write.
         * Updates the mapped memory are not made visible
         * to other processes.
         */
        val PRIVATE: MappingFlags = MappingFlags(2)

        /**
         * A mapping which may be shared between two
         * or more processes. This will cause the memory block
         * to be updated in the other processes virtual address spaces
         * when being written to.
         */
        val SHARED: MappingFlags = MappingFlags(4)
    }

    operator fun plus(flags: MappingFlags): MappingFlags = MappingFlags(value or flags.value)
    operator fun contains(flags: MappingFlags): Boolean = value and flags.value == flags.value
}