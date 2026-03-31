plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.keyscript.plugin"
version = "2.2.0"

// ─── Cross-platform IDE path resolution ─────────────────────────────
// Set `ideaPath` in gradle.properties (project-local or ~/.gradle/gradle.properties)
// to point to your local IntelliJ IDEA installation. This avoids downloading the
// full SDK (~3GB) on every clean build.
//
// Examples:
//   macOS:   ideaPath=/Users/you/Applications/IntelliJ IDEA.app/Contents
//   Windows: ideaPath=C:\\Program Files\\JetBrains\\IntelliJ IDEA 2026.1
//   Linux:   ideaPath=/opt/intellij-idea/idea-IU-265.xxxxx.xx
//
// If not set, falls back to downloading IntelliJ IDEA Community 2026.1.
val ideaPath: String? = providers.gradleProperty("ideaPath").orNull

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        if (ideaPath != null) {
            local(ideaPath!!)
        } else {
            intellijIdeaCommunity("2026.1")
        }
        bundledPlugin("JavaScript")
        jetbrainsRuntime()
    }

    // Ktor 3.4.1 — compatible with IntelliJ 2026.1's bundled Kotlin 2.3.0
    // Ktor version must match the Kotlin stdlib bundled by the target IntelliJ platform.
    val ktorVersion = "3.4.1"
    // Exclude kotlinx-coroutines — IntelliJ provides its own and they conflict at runtime
    // (ServiceConfigurationError: CoroutineExceptionHandler not a subtype)
    val excludeCoroutines: ExternalModuleDependency.() -> Unit = {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion", excludeCoroutines)
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion", excludeCoroutines)
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion", excludeCoroutines)
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion", excludeCoroutines)
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion", excludeCoroutines)
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion", excludeCoroutines)
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion", excludeCoroutines)
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion", excludeCoroutines)

    // JSON
    val jacksonVersion = "2.18.2" // Stable version
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // Testing
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    // instrumentCode disabled: java-compiler-ant-tasks resolution fails with local SDK
    instrumentCode = false

    pluginConfiguration {
        name = "Keyscript IDE"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "265"
            untilBuild = "265.*"
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    wrapper {
        gradleVersion = "9.0"
    }

    // buildSearchableOptions disabled: requires full IDE context unavailable with local SDK
    buildSearchableOptions {
        enabled = false
    }

    runIde {
        jvmArgs(
            "-Xmx2g",
            "-Xms512m"
        )
    }
}
