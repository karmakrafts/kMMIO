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
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.dokka)
    `maven-publish`
}

kotlin {
    mingwX64()
    linuxX64()
    linuxArm64()
    androidNativeX64()
    androidNativeArm64()
    androidNativeArm32()
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    applyDefaultHierarchyTemplate()
    sourceSets {
        val posixMain by creating {
            dependsOn(nativeMain.get())
        }
        linuxMain {
            dependsOn(posixMain)
        }
        androidNativeMain {
            dependsOn(posixMain)
        }
        macosMain {
            dependsOn(posixMain)
        }
        iosMain {
            dependsOn(posixMain)
        }
        commonMain {
            dependencies {
                api(libs.kotlinx.io.bytestring)
                api(libs.kotlinx.io.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        configureEach {
            if ("Main" !in name) return@configureEach
            withSourcesJar(true)
        }
    }
}

dokka {
    moduleName = project.name
    pluginsConfiguration {
        html {
            footerMessage = "(c) 2025 Karma Krafts & associates"
        }
    }
}

val dokkaJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaGeneratePublicationHtml)
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

tasks {
    System.getProperty("publishDocs.root")?.let { docsDir ->
        register("publishDocs", Copy::class) {
            dependsOn(dokkaJar)
            mustRunAfter(dokkaJar)
            from(zipTree(dokkaJar.get().outputs.files.first()))
            into(docsDir)
        }
    }
}

publishing {
    repositories {
        with(CI) { authenticatedPackageRegistry() }
    }
    publications {
        withType<MavenPublication> {
            artifact(dokkaJar)
            pom {
                name = project.name
                description = "Lightweight MMIO for Kotlin Multiplatform."
                url = System.getenv("CI_PROJECT_URL")
                licenses {
                    license {
                        name = "Apache License 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer {
                        id = "kitsunealex"
                        name = "KitsuneAlex"
                        url = "https://git.karmakrafts.dev/KitsuneAlex"
                    }
                }
                scm {
                    url = this@pom.url
                }
            }
        }
    }
}