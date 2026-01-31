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

@file:Suppress("Since15") // We compile against Panama as a preview feature to be compatible with Java 21

package dev.karmakrafts.kmmio

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.invoke.MethodHandle

private val libCLookup: SymbolLookup = if (isWindows) SymbolLookup.libraryLookup("msvcrt", Arena.global())
else Linker.nativeLinker().defaultLookup()

internal fun getNativeFunction( // @formatter:off
    name: String,
    descriptor: FunctionDescriptor,
    vararg options: Linker.Option
): MethodHandle { // @formatter:on
    val address = libCLookup.find(name).orElseThrow()
    return Linker.nativeLinker().downcallHandle(address, descriptor, *options)
}