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

internal const val O_RDONLY: Int = 0x00
internal const val O_WRONLY: Int = 0x01
internal const val O_RDWR: Int = 0x02

internal val O_CREAT: Int = when {
    isMacos -> 0x200
    isWindows -> 0x100
    else -> 0x40
}

internal const val MAP_NONE: Int = 0x00
internal const val MAP_SHARED: Int = 0x01
internal const val MAP_PRIVATE: Int = 0x02
internal val MAP_ANON: Int = if (isMacos) 0x1000 else 0x20

internal const val PROT_NONE: Int = 0x00
internal const val PROT_READ: Int = 0x01
internal const val PROT_WRITE: Int = 0x02
internal const val PROT_EXEC: Int = 0x04

internal const val S_IRUSR: Int = 0x100
internal const val S_IWUSR: Int = 0x80
internal const val S_IXUSR: Int = 0x40
internal const val S_IRGRP: Int = 0x20
internal const val S_IWGRP: Int = 0x10
internal const val S_IXGRP: Int = 0x08
internal const val S_IROTH: Int = 0x04
internal const val S_IWOTH: Int = 0x02
internal const val S_IXOTH: Int = 0x01

internal fun AccessFlags.toPosixFileMask(): Int {
    var mask = S_IRUSR or S_IRGRP or S_IROTH
    if (AccessFlags.WRITE in this) mask = mask or (S_IWUSR or S_IWGRP or S_IWOTH)
    if (AccessFlags.EXEC in this) mask = mask or (S_IXUSR or S_IXGRP or S_IXOTH)
    return mask
}

internal fun AccessFlags.toPosixFlags(): Int {
    var result = PROT_NONE
    if (AccessFlags.READ in this) result = result or PROT_READ
    if (AccessFlags.WRITE in this) result = result or PROT_WRITE
    if (AccessFlags.EXEC in this) result = result or PROT_EXEC
    return result
}

internal fun AccessFlags.toPosixOpenFlags(): Int = when {
    AccessFlags.READ in this && AccessFlags.WRITE in this -> O_RDWR
    this == AccessFlags.READ -> O_RDONLY
    this == AccessFlags.WRITE -> O_WRONLY
    else -> error("Unsupported access flags in VirtualMemory: 0x${value.toHexString()}")
}

internal fun MappingFlags.toPosixFlags(): Int {
    var result = MAP_NONE
    if (MappingFlags.ANON in this) result = result or MAP_ANON
    if (MappingFlags.SHARED in this) result = result or MAP_SHARED
    if (MappingFlags.PRIVATE in this) result = result or MAP_PRIVATE
    return result
}

internal fun Int.checkPosixResult() {
    check(this == 0) { "Function did not return successfully: error 0x${toHexString()}" }
}