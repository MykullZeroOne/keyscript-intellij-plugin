plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "com.keyscript.plugin"
version = "2.1.0"

// ─── Cross-platform IDE path resolution ─────────────────────────────
// Set `ideaPath` in gradle.properties (project-local or ~/.gradle/gradle.properties)
// to point to your local IntelliJ IDEA installation. This avoids downloading the
// full SDK (~3GB) on every clean build.
//
// Examples:
//   macOS:   ideaPath=/Users/you/Applications/IntelliJ IDEA.app/Contents
//   Windows: ideaPath=C:\\Program Files\\JetBrains\\IntelliJ IDEA 2025.3.3
//   Linux:   ideaPath=/opt/intellij-idea/idea-IU-253.32098.37
//
// If not set, falls back to downloading IntelliJ IDEA Community 2025.3.3.
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
            intellijIdeaCommunity("2025.3.3")
        }
        bundledPlugin("JavaScript")
        jetbrainsRuntime()
    }

    // Ktor 3.2.4 — latest version compatible with IntelliJ 2025.3's bundled Kotlin 2.1.x
    // Ktor 3.3+ requires Kotlin 2.2+, Ktor 3.4+ requires Kotlin 2.3+ — both are
    // incompatible with the kotlin-stdlib provided by IntelliJ 2025.3 at runtime.
    val ktorVersion = "3.2.4"
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
            sinceBuild = "253"
            untilBuild = "253.*"
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
        gradleVersion = "8.11.1"
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
