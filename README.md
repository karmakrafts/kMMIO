# Multiplatform mman

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