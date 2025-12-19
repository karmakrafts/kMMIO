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

import platform.posix.O_RDONLY
import platform.posix.O_RDWR
import platform.posix.O_WRONLY

internal fun AccessFlags.toPosixOpenFlags(): Int = when {
    AccessFlags.READ in this && AccessFlags.WRITE in this -> O_RDWR
    this == AccessFlags.READ -> O_RDONLY
    this == AccessFlags.WRITE -> O_WRONLY
    else -> error("Unsupported access flags in VirtualMemory: 0x${value.toHexString()}")
}

internal fun Int.checkPosixResult() {
    check(this == 0) { "Function did not return successfully: error 0x${toHexString()}" }
}