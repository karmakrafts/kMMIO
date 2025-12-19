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

/**
 * An interface for objects that support random access to their contents.
 */
interface RandomAccess {
    /**
     * The position from which to seek.
     */
    enum class Whence {
        /**
         * Seek from the start of the content.
         */
        START,

        /**
         * Seek from the current position.
         */
        CURRENT,

        /**
         * Seek from the end of the content.
         */
        END
    }

    /**
     * Sets the position for the next read or write operation.
     *
     * @param offset the offset from the [whence] position
     * @param whence the position from which the [offset] is applied
     */
    fun seek(offset: Long, whence: Whence = Whence.CURRENT)

    /**
     * Returns the current position.
     *
     * @return the current position
     */
    fun tell(): Long
}