# Multiplatform mman
[![](https://git.karmakrafts.dev/kk/multiplatform-mman/badges/master/pipeline.svg)](https://git.karmakrafts.dev/kk/multiplatform-mman/-/pipelines)
[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Ffiles.karmakrafts.dev%2Fmaven%2Fio%2Fkarma%2Fmman%2Fmultiplatform-mman%2Fmaven-metadata.xml)](https://git.karmakrafts.dev/kk/multiplatform-mman/-/packages)

Lightweight wrapper around mman for Kotlin/Native to allow for easy MMIO.

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
                implementation("io.karma.mman:multiplatform-mman:<version>")
            }
        }
    }
}
```

Afterwards, you can access all APIs from any native source set:

```kotlin
import io.karma.mman.AccessFlags
import io.karma.mman.AccessFlags.Companion
import io.karma.mman.MemoryRegion

fun main() {
    MemoryRegion.map(path, AccessFlags.READ + AccessFlags.WRITE).use {
        it.asSource() // Consume data through a kotlinx.io.Source
        it.asSink() // Produce data through a kotlinx.io.Sink
    }
}
```