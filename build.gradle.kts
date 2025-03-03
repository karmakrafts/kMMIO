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

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.dokka) apply false
}

group = "io.karma.kmmio"
version = CI.getDefaultVersion(libs.versions.kmmio)

allprojects {
    repositories {
        mavenCentral()
        google()
        karmakrafts()
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version
    configureJava(rootProject.libs.versions.java)
    with(CI) { configure() }
}