# kMMIO
[![](https://git.karmakrafts.dev/kk/kmmio/badges/master/pipeline.svg)](https://git.karmakrafts.dev/kk/kmmio/-/pipelines)
[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Ffiles.karmakrafts.dev%2Fmaven%2Fio%2Fkarma%2Fkmmio%2Fkmmio%2Fmaven-metadata.xml)](https://git.karmakrafts.dev/kk/kmmio/-/packages)

Common MMIO API for Kotlin Multiplatform based on [kotlinx.io](https://github.com/Kotlin/kotlinx-io).  
This library was developed as part of the [Kleaver](https://git.karmakrafts.dev/karmastudios/kleaver) project.

### Platform support

* Windows x64
* Linux x64
* Linux arm64
* macOS x64
* macOS arm64
* iOS x64
* iOS arm64
* Android Native x64
* Android Native arm64
* Android Native arm32

Support for the JVM is planned.

### How to use it

First, add a dependency on the library:

```kotlin
repositories {
    maven("https://files.karmakrafts.dev/maven")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("io.karma.kmmio:kmmio:<version>")
            }
        }
    }
}
```

Afterwards, you can access all APIs from any native source set:

```kotlin
import io.karma.kmmio.AccessFlags
import io.karma.kmmio.AccessFlags.Companion
import io.karma.kmmio.MemoryRegion

fun main() {
    MemoryRegion.map(path, AccessFlags.READ + AccessFlags.WRITE).use {
        it.asSource() // Consume data through a kotlinx.io.Source
        it.asSink() // Produce data through a kotlinx.io.Sink
    }
}
```