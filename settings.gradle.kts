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

rootProject.name = "kmmio"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
        maven("https://files.karmakrafts.dev/maven")
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage") repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://files.karmakrafts.dev/maven")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "0.9.0"
}

include("kmmio")