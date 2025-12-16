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

@file:Suppress("Since15") // We compile against Panama as a preview feature to be compatible with Java 21

package dev.karmakrafts.kmmio

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.invoke.MethodHandle

internal val isWindows: Boolean = "win" in System.getProperty("os.name").lowercase()
internal val isMacos: Boolean = "mac" in System.getProperty("os.name").lowercase()

internal fun getNativeFunction( // @formatter:off
    name: String,
    descriptor: FunctionDescriptor,
    vararg options: Linker.Option
): MethodHandle { // @formatter:on
    val linker = Linker.nativeLinker()
    val address = linker.defaultLookup().find(name).orElseThrow()
    return linker.downcallHandle(address, descriptor, *options)
}