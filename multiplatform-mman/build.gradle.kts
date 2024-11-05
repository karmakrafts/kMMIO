import org.jetbrains.kotlin.gradle.tasks.CInteropProcess

/*
 * Copyright 2024 Karma Krafts & associates
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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    mingwX64()
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    applyDefaultHierarchyTemplate()
    sourceSets {
        val posixMain by creating { dependsOn(nativeMain.get()) }
        linuxMain { dependsOn(posixMain) }
        macosMain { dependsOn(posixMain) }
        commonMain {
            dependencies {
                implementation(libs.kotlinx.io.bytestring)
                implementation(libs.kotlinx.io.core)
                implementation(libs.kotest.engine)
            }
        }
    }
}

val dokkaJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaHtml)
    from(tasks.dokkaHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

tasks {
    // Force disable caching for CInterop tasks
    withType<CInteropProcess>().configureEach {
        outputs.cacheIf { false }
    }
    dokkaHtml {
        dokkaSourceSets.create("main") {
            reportUndocumented = false
            jdkVersion = java.toolchain.languageVersion.get().asInt()
            noAndroidSdkLink = true
            externalDocumentationLink("https://docs.karmakrafts.dev/${rootProject.name}")
        }
    }
    System.getProperty("publishDocs.root")?.let { docsDir ->
        create<Copy>("publishDocs") {
            mustRunAfter(dokkaJar)
            from(zipTree(dokkaJar.get().outputs.files.first()))
            into(docsDir)
        }
    }
}

publishing {
    System.getenv("CI_API_V4_URL")?.let { apiUrl ->
        repositories {
            maven {
                url = uri("$apiUrl/projects/${System.getenv("CI_PROJECT_ID")}/packages/maven")
                name = "GitLab"
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = System.getenv("CI_JOB_TOKEN")
                }
                authentication {
                    create("header", HttpHeaderAuthentication::class)
                }
            }
        }
    }
    publications.configureEach {
        if (this is MavenPublication) {
            artifact(dokkaJar)
            pom {
                name = project.name
                description = "Multiplatform bindings for mman on Linux, Windows and macOS."
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