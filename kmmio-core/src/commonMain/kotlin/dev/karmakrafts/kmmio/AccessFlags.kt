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

import kotlin.jvm.JvmInline

/**
 * The type of permissions required by a [VirtualMemory] in
 * order to read, write or execute mapped memory blocks.
 */
@JvmInline
value class AccessFlags private constructor(private val value: UByte) {
    companion object {
        /**
         * The mapped memory may be read by the process that owns the mapping.
         */
        val READ: AccessFlags = AccessFlags(0x01U)

        /**
         * The mapped memory may be written to by the process that owns the mapping.
         */
        val WRITE: AccessFlags = AccessFlags(0x02U)

        /**
         * The mapped memory may be executed in the context of the process
         * that owns the mapping.
         */
        val EXEC: AccessFlags = AccessFlags(0x04U)
    }

    operator fun plus(flags: AccessFlags): AccessFlags = AccessFlags(value or flags.value)
    operator fun contains(flags: AccessFlags): Boolean = value and flags.value == flags.value
}