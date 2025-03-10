/*
 * Copyright 2025 Karma Krafts & associates
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

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.maybeCreate
import org.gradle.kotlin.dsl.provideDelegate
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.notExists

private val gson: Gson = Gson()

fun String.percentEncode(): String {
    val specialChars = ":/?#[]@!$&'()*+,;="
    var encoded = ""
    for (char in this) {
        if (char !in specialChars) {
            encoded += char
            continue
        }
        encoded += "%${char.code.toUByte().toString(16).uppercase()}"
    }
    return encoded
}

fun DirectoryProperty.getFile(): File = get().asFile
fun DirectoryProperty.getPath(): Path = get().asFile.toPath()
operator fun DirectoryProperty.div(other: String): Path = getPath() / other
operator fun DirectoryProperty.div(other: Path): Path = getPath() / other

fun TaskContainer.ensureBuildDirectory(): Task {
    // Lazily registers this task when called and not present
    return if (any { it.name == "ensureBuildDirectory" }) this["ensureBuildDirectory"]
    else maybeCreate("ensureBuildDirectory").apply {
        val path = project.layout.buildDirectory.get().asFile.toPath()
        doLast { path.createDirectories() }
        onlyIf { path.notExists() }
    }
}

@Suppress("UnstableApiUsage")
fun Int.toJavaVersion(): JavaVersion = when (this) {
    1 -> JavaVersion.VERSION_1_1
    2 -> JavaVersion.VERSION_1_2
    3 -> JavaVersion.VERSION_1_3
    4 -> JavaVersion.VERSION_1_4
    5 -> JavaVersion.VERSION_1_5
    6 -> JavaVersion.VERSION_1_6
    7 -> JavaVersion.VERSION_1_7
    8 -> JavaVersion.VERSION_1_8
    9 -> JavaVersion.VERSION_1_9
    10 -> JavaVersion.VERSION_1_10
    11 -> JavaVersion.VERSION_11
    12 -> JavaVersion.VERSION_12
    13 -> JavaVersion.VERSION_13
    14 -> JavaVersion.VERSION_14
    15 -> JavaVersion.VERSION_15
    16 -> JavaVersion.VERSION_16
    17 -> JavaVersion.VERSION_17
    18 -> JavaVersion.VERSION_18
    19 -> JavaVersion.VERSION_19
    20 -> JavaVersion.VERSION_20
    21 -> JavaVersion.VERSION_21
    22 -> JavaVersion.VERSION_22
    23 -> JavaVersion.VERSION_23
    24 -> JavaVersion.VERSION_24
    25 -> JavaVersion.VERSION_25
    26 -> JavaVersion.VERSION_26
    27 -> JavaVersion.VERSION_27
    else -> throw IllegalArgumentException("Unrecognized Java version: $this")
}

fun Project.configureJava(version: Int) {
    project.pluginManager.apply {
        logger.lifecycle("Setting up project using Java $version")
        withPlugin("java") {
            extensions.getByType(JavaPluginExtension::class).apply {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(version))
                }
                val javaVersion = version.toJavaVersion()
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
        }
        withPlugin("org.jetbrains.kotlin.jvm") {
            logger.lifecycle("Found Kotlin JVM plugin, adjusting Java version")
            extensions.getByName("kotlin").apply {
                this::class.java.getMethod("jvmToolchain", Int::class.java).invoke(this, version)
            }
        }
        withPlugin("org.jetbrains.kotlin.multiplatform") {
            logger.lifecycle("Found Kotlin Multiplatform plugin, adjusting Java version")
            extensions.getByName("kotlin").apply {
                this::class.java.getMethod("jvmToolchain", Int::class.java).invoke(this, version)
            }
        }
    }
}

fun Project.configureJava(provider: Provider<String>) {
    configureJava(provider.get().toInt())
}

fun RepositoryHandler.karmakrafts() {
    maven("https://files.karmakrafts.dev/maven")
}

// Static CI helpers

object CI {
    val isCI: Boolean
        get() = System.getenv("CI_PROJECT_ID") != null

    fun Project.configure() {
        if (!isCI) return
        dependencyLocking {
            lockAllConfigurations()
        }
        tasks.register("dependenciesForAll", DependencyReportTask::class.java)
    }

    fun getDefaultVersion(baseVersion: Provider<String>): String {
        return System.getenv("CI_COMMIT_TAG")?.let { baseVersion.get() } ?: "${baseVersion.get()}.${
            System.getenv(
                "CI_PIPELINE_IID"
            ) ?: 0
        }-SNAPSHOT"
    }

    fun RepositoryHandler.authenticatedPackageRegistry() {
        System.getenv("CI_API_V4_URL")?.let { apiUrl ->
            maven {
                url = URI.create("$apiUrl/projects/${System.getenv("CI_PROJECT_ID")}/packages/maven")
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
}

// Minimal abstraction for working with the GitLab API to make it safe(r)

private fun URLConnection.setDefaultUserAgent() {
    setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0")
}

class GitLabServer(
    val project: Project, val address: String
) {
    private val projects: HashMap<String, GitLabProject> = HashMap()
    val apiUrl: String = "https://$address/api/v4"

    fun project(path: String, name: String = path.substringAfterLast('/')): GitLabProject {
        val endpoint = "$apiUrl/projects/${path.percentEncode()}"
        return projects.getOrPut(endpoint) { GitLabProject(this, endpoint, name, null) }
    }

    fun project(id: Long, name: String = id.toString()): GitLabProject {
        val endpoint = "$apiUrl/projects/$id"
        return projects.getOrPut(endpoint) { GitLabProject(this, endpoint, name, id) }
    }
}

class GitLabProject(
    val server: GitLabServer, val endpoint: String, val name: String, givenId: Long?
) {
    val projectInfo: JsonObject = with(URI.create(endpoint).toURL().openConnection() as HttpURLConnection) {
        setDefaultUserAgent()
        requestMethod = "GET"
        inputStream.bufferedReader().use {
            gson.fromJson(it, JsonObject::class.java)
        }
    }

    val id: Long = givenId ?: projectInfo["id"].asLong
    val endpointWithId: String = "${server.apiUrl}/projects/$id"
    val packageRegistry: GitLabPackageRegistry = GitLabPackageRegistry(this, "$endpointWithId/packages")
}

class GitLabPackageRegistry(
    val project: GitLabProject, val endpoint: String
) {
    private val packages: HashMap<String, GitLabPackage> = HashMap()

    operator fun get(path: String): GitLabPackage {
        val url = "$endpoint/$path"
        return packages.getOrPut(url) { GitLabPackage(this, url) }
    }

    operator fun get(path: String, version: String): GitLabPackage {
        val url = "$endpoint/$path/$version"
        return packages.getOrPut(url) { GitLabPackage(this, url) }
    }

    operator fun get(path: String, version: Provider<in String>): GitLabPackage {
        val url = "$endpoint/$path/${version.get()}"
        return packages.getOrPut(url) { GitLabPackage(this, url) }
    }
}

fun RepositoryHandler.packageRegistry(registry: GitLabPackageRegistry) {
    maven("${registry.endpoint}/maven")
}

fun RepositoryHandler.packageRegistry(project: GitLabProject) {
    maven("${project.packageRegistry.endpoint}/maven")
}

class GitLabPackage(
    val packageRegistry: GitLabPackageRegistry, val url: String
) {
    private val artifacts: HashMap<String, GitLabPackageArtifact> = HashMap()

    private fun getArtifactKey(fileName: String, suffix: String, directoryName: String): String {
        return if (suffix.isEmpty()) fileName
        else "$fileName:$suffix@$directoryName"
    }

    operator fun get(
        fileName: String, suffix: String = "", directoryName: String = packageRegistry.project.name
    ): GitLabPackageArtifact {
        return artifacts.getOrPut(getArtifactKey(fileName, suffix, directoryName)) {
            GitLabPackageArtifact(
                this, url, fileName, suffix, directoryName
            )
        }
    }

    operator fun get(
        fileName: Provider<String>, suffix: String = "", directoryName: String = packageRegistry.project.name
    ): GitLabPackageArtifact {
        val actualFileName = fileName.get()
        return artifacts.getOrPut(getArtifactKey(actualFileName, suffix, directoryName)) {
            GitLabPackageArtifact(
                this, url, actualFileName, suffix, directoryName
            )
        }
    }
}

class GitLabPackageArtifact(
    val packageInstance: GitLabPackage,
    val packageUrl: String,
    val fileName: String,
    val suffix: String,
    val directoryName: String
) {
    private val projectName: String = packageInstance.packageRegistry.project.name
    private val project: Project = packageInstance.packageRegistry.project.server.project
    val localDirectoryPath: Path by lazy { project.layout.buildDirectory / directoryName }
    val localPath: Path by lazy { localDirectoryPath / fileName }

    val outputDirectoryPath: Path by lazy {
        if (suffix.isBlank()) localDirectoryPath
        else localDirectoryPath / suffix
    }

    val downloadTask: Task =
        project.tasks.maybeCreate("download${projectName.capitalized()}${suffix.capitalized()}").apply {
            group = projectName
            doLast {
                val url = "$packageUrl/$fileName"
                println("Downloading $url..")
                localPath.createDirectories()
                URI.create(url).toURL().openConnection().apply {
                    setDefaultUserAgent()
                    connect()
                    inputStream.use { Files.copy(it, localPath, StandardCopyOption.REPLACE_EXISTING) }
                }
                println("Downloaded $localPath")
            }
            onlyIf { !localPath.exists() }
        }

    val extractTask: Copy = project.tasks.maybeCreate(
        "extract${projectName.capitalized()}${suffix.capitalized()}", Copy::class
    ).apply {
        group = projectName
        dependsOn(downloadTask)
        mustRunAfter(downloadTask)
        from(project.zipTree(localPath.toFile()))
        into(outputDirectoryPath.toFile())
        doLast { println("Extracted $localPath") }
    }

    val cleanTask: Task = project.tasks.maybeCreate("clean${projectName.capitalized()}${suffix.capitalized()}").apply {
        doLast {
            localPath.deleteIfExists()
            println("Removed $localPath")
        }
    }
}

private val gitlabServers: HashMap<String, GitLabServer> = HashMap()

fun Project.gitlab(
    address: String = "git.karmakrafts.dev"
): GitLabServer = gitlabServers.getOrPut(address) { GitLabServer(this, address) }

// Minimal abstraction for working with git repositories

class GitRepository(
    val project: Project, val name: String, val address: String, val tag: String?, val group: String
) {
    val localPath: Path = project.layout.buildDirectory / name

    val cloneTask: Exec = project.tasks.maybeCreate("clone${name.replace("-", "").capitalized()}", Exec::class).apply {
        group = this@GitRepository.group
        dependsOn(project.tasks.ensureBuildDirectory())
        workingDir = project.layout.buildDirectory.getFile()
        if (tag != null) commandLine(
            "git", "clone", "--branch", tag, "--single-branch", address, this@GitRepository.name
        )
        else commandLine("git", "clone", address, this@GitRepository.name)
        onlyIf { localPath.notExists() }
    }

    val pullTask: Exec = project.tasks.maybeCreate("pull${name.replace("-", "").capitalized()}", Exec::class).apply {
        group = this@GitRepository.group
        dependsOn(cloneTask)
        workingDir = localPath.toFile()
        commandLine("git", "pull", "--force")
        onlyIf { localPath.exists() }
    }
}

fun Project.gitRepository(
    name: String, address: String, tag: String? = null, group: String = name
): GitRepository = GitRepository(this, name, address, tag, group)