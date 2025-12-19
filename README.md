# kMMIO

[![](https://git.karmakrafts.dev/kk/kmmio/badges/master/pipeline.svg)](https://git.karmakrafts.dev/kk/kmmio/-/pipelines)
[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo.maven.apache.org%2Fmaven2%2Fdev%2Fkarmakrafts%2Fkmmio%2Fkmmio-core%2Fmaven-metadata.xml
)](https://git.karmakrafts.dev/kk/kmmio/-/packages)
[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fkarmakrafts%2Fkmmio%2Fkmmio-core%2Fmaven-metadata.xml
)](https://git.karmakrafts.dev/kk/kmmio/-/packages)
[![](https://img.shields.io/badge/2.3.0-blue?logo=kotlin&label=kotlin)](https://kotlinlang.org/)
[![](https://img.shields.io/badge/documentation-black?logo=kotlin)](https://docs.karmakrafts.dev/kmmio-core)

Lightweight memory mapped IO for Kotlin Multiplatform on JVM, Android and native.  
If you need random access on big files, this is the library you're looking for!

This library also comes with [kotlinx.io](https://github.com/Kotlin/kotlinx-io) integration out of the box.

### How to use it

First, add the official Maven Central repository to your settings.gradle.kts:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Then add a dependency on the library in your buildscript:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("dev.karmakrafts.kmmio:kmmio-core:<version>")
            }
        }
    }
}
```

### Code example

```kotlin
fun main() {
    MemoryRegion.map(path, AccessFlags.READ + AccessFlags.WRITE).use {
        it.asSource() // Consume data through a kotlinx.io.Source
        it.asSink() // Produce data through a kotlinx.io.Sink
    }
}
```