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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessFlagsTest {
    @Test
    fun constantsAreDistinctAndContainThemselves() {
        val flags = listOf(AccessFlags.READ, AccessFlags.WRITE, AccessFlags.EXEC)
        assertEquals(3, flags.toSet().size)
        for (flag in flags) {
            for (other in flags) assertEquals(flag == other, other in flag)
            assertEquals(flag, flag + flag)
        }
    }

    @Test
    fun combinationsContainAllAndOnlyTheirMembers() {
        val flags = listOf(AccessFlags.READ, AccessFlags.WRITE, AccessFlags.EXEC)
        for (first in flags) {
            for (second in flags) {
                val combined = first + second
                assertEquals(combined, second + first)
                assertEquals(combined.hashCode(), (second + first).hashCode())
                assertTrue(first in combined)
                assertTrue(second in combined)
                assertTrue(combined in combined)
                for (other in flags) {
                    if (other != first && other != second) assertFalse(other in combined)
                }
            }
        }
        assertFalse(AccessFlags.READ + AccessFlags.WRITE in AccessFlags.READ)
        assertEquals(
            (AccessFlags.READ + AccessFlags.WRITE) + AccessFlags.EXEC,
            AccessFlags.READ + (AccessFlags.WRITE + AccessFlags.EXEC)
        )
    }
}