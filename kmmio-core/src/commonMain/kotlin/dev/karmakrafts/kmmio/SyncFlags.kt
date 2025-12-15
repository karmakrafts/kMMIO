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
 * The type of synchronization operation to perform when
 * calling [VirtualMemory.sync].
 */
@JvmInline
value class SyncFlags private constructor(private val value: Int) {
    companion object {
        /**
         * Schedules an update and waits for its completion.
         */
        val SYNC: SyncFlags = SyncFlags(1)

        /**
         * Schedules an update and immediately returns,
         * not waiting for the update to complete.
         */
        val ASYNC: SyncFlags = SyncFlags(2)

        /**
         * Invalidate other mappings of the underlying file
         * to update the newly written contents.
         */
        val INVALIDATE: SyncFlags = SyncFlags(4)
    }

    operator fun plus(flags: SyncFlags): SyncFlags = SyncFlags(value or flags.value)
    operator fun contains(flags: SyncFlags): Boolean = value and flags.value == flags.value
}