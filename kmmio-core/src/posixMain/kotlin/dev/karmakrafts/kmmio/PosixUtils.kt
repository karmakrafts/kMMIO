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

import platform.posix.MAP_ANON
import platform.posix.MAP_PRIVATE
import platform.posix.MAP_SHARED
import platform.posix.PROT_EXEC
import platform.posix.PROT_NONE
import platform.posix.PROT_READ
import platform.posix.PROT_WRITE

internal fun AccessFlags.toPosixFlags(): Int {
    var result = PROT_NONE
    if (AccessFlags.READ in this) result = result or PROT_READ
    if (AccessFlags.WRITE in this) result = result or PROT_WRITE
    if (AccessFlags.EXEC in this) result = result or PROT_EXEC
    return result
}

internal fun MappingFlags.toPosixFlags(): Int {
    var result = 0
    if (MappingFlags.ANON in this) result = result or MAP_ANON
    if (MappingFlags.SHARED in this) result = result or MAP_SHARED
    if (MappingFlags.PRIVATE in this) result = result or MAP_PRIVATE
    return result
}