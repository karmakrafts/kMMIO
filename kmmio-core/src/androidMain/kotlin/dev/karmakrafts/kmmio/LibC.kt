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

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

internal interface LibC : Library {
    companion object : LibC by Native.load("c", LibC::class.java)

    fun open(path: Pointer, oflags: Int /*, ... */): Int
    fun close(fd: Int): Int
    fun ftruncate(fd: Int, length: Long): Int

    fun memcpy(dst: Pointer, src: Pointer, size: Long): Pointer
    fun memset(addr: Pointer, value: Int, size: Long): Pointer

    fun mmap(addr: Pointer?, length: Long, prot: Int, flags: Int, fd: Int, offset: Long): Pointer?
    fun munmap(addr: Pointer?, length: Long): Int
    fun msync(addr: Pointer?, length: Long, flags: Int): Int
    fun mprotect(addr: Pointer?, length: Long, prot: Int): Int
    fun mlock(addr: Pointer?, length: Long): Int
    fun munlock(addr: Pointer?, length: Long): Int
}