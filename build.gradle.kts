plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "com.keyscript.plugin"
version = "2.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.3.3")
        bundledPlugin("JavaScript")
        jetbrainsRuntime()
    }

    // Ktor 3.4.1 for embedded proxy server (CIO engine — lightweight, no Netty)
    val ktorVersion = "3.4.1"
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cors-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
}

intellijPlatform {
    instrumentCode = false

    pluginConfiguration {
        name = "Keyscript IDE"
        version = "2.0.0"

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

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        jvmArgs(
            "-Dsun.java2d.metal=false",
            "-Xmx2g",
            "-Xms512m"
        )
    }
}
