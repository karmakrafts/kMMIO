/*
 * Copyright 2025 (C) Karma Krafts & associates
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

package io.karma.kmmio

/**
 * Denotes a declaration which is internal to Multiplatform mman
 * but may be used by opting in using [OptIn].
 */
@RequiresOptIn(message = "This API is internal to Mman and shouldn't be used directly")
@Retention(AnnotationRetention.BINARY)
annotation class InternalMmanApi
