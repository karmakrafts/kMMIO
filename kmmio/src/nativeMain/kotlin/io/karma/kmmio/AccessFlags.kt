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

/**
 * The type of permissions required by a [MemoryRegion] in
 * order to read, write or execute mapped memory blocks.
 */
value class AccessFlags private constructor(private val value: Int) {
    companion object {
        /**
         * The mapped memory may be read by the process that owns the mapping.
         */
        val READ: AccessFlags = AccessFlags(1)

        /**
         * The mapped memory may be written to by the process that owns the mapping.
         */
        val WRITE: AccessFlags = AccessFlags(2)

        /**
         * The mapped memory may be executed in the context of the process
         * that owns the mapping.
         */
        val EXEC: AccessFlags = AccessFlags(4)
    }

    operator fun plus(flags: AccessFlags): AccessFlags = AccessFlags(value or flags.value)
    operator fun contains(flags: AccessFlags): Boolean = value and flags.value == flags.value
}