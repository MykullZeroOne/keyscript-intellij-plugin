package com.keyscript.plugin.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import java.io.File

/**
 * Bundles React/Node projects using esbuild.
 * Reads configuration from keyscript.bundle.json in the project root.
 *
 * Expected config shape:
 * {
 *   "entry": "src/index.jsx",
 *   "outfile": "dist/bundle.js",
 *   "format": "iife",
 *   "target": "es2020",
 *   "minify": false,
 *   "jsx": "automatic",
 *   "external": ["react", "react-dom"],
 *   "define": { "process.env.NODE_ENV": "\"production\"" }
 * }
 */
@Service(Service.Level.PROJECT)
class BundleService(private val project: Project) {
    private val log = Logger.getInstance(BundleService::class.java)
    private val mapper = jacksonObjectMapper()

    data class BundleConfig(
        val entry: String = "src/index.jsx",
        val outfile: String = "dist/bundle.js",
        val format: String = "iife",
        val target: String = "es2020",
        val minify: Boolean = false,
        val jsx: String = "automatic",
        val external: List<String> = emptyList(),
        val define: Map<String, String> = emptyMap(),
        val inject: List<String> = emptyList(),
        val loader: Map<String, String> = emptyMap()
    )

    data class BundleResult(
        val success: Boolean,
        val outputPath: String? = null,
        val outputSize: Long = 0,
        val error: String? = null,
        val stderr: String? = null
    )

    /**
     * Read keyscript.bundle.json from project root, or null if not present.
     */
    fun readConfig(): BundleConfig? {
        val basePath = project.basePath ?: return null
        val configFile = File(basePath, "keyscript.bundle.json")
        if (!configFile.exists()) return null
        return try {
            mapper.readValue(configFile)
        } catch (e: Exception) {
            log.warn("Failed to parse keyscript.bundle.json", e)
            null
        }
    }

    /**
     * Check if the project has a bundle configuration.
     */
    fun hasBundleConfig(): Boolean {
        val basePath = project.basePath ?: return false
        return File(basePath, "keyscript.bundle.json").exists()
    }

    /**
     * Locate the esbuild binary. Checks:
     * 1. Project-local node_modules/.bin/esbuild
     * 2. Global npx esbuild
     * 3. Global esbuild on PATH
     */
    fun findEsbuild(): String? {
        val basePath = project.basePath ?: return null
        val root = File(basePath)

        // Project-local
        val localBin = File(root, "node_modules/.bin/esbuild")
        if (localBin.exists() && localBin.canExecute()) return localBin.absolutePath

        // Check PATH
        return try {
            val process = ProcessBuilder("which", "esbuild")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && result.isNotEmpty()) result else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Run esbuild with the project's bundle configuration.
     */
    fun bundle(config: BundleConfig? = null, watch: Boolean = false): BundleResult {
        val cfg = config ?: readConfig()
            ?: return BundleResult(success = false, error = "No keyscript.bundle.json found in project root")

        val esbuild = findEsbuild()
            ?: return BundleResult(
                success = false,
                error = "esbuild not found. Install it with: npm install esbuild --save-dev"
            )

        val basePath = project.basePath
            ?: return BundleResult(success = false, error = "Cannot determine project path")
        val root = File(basePath)

        // Ensure output directory exists
        val outFile = File(root, cfg.outfile)
        outFile.parentFile?.mkdirs()

        // Build command
        val cmd = mutableListOf(esbuild, cfg.entry)
        cmd.add("--bundle")
        cmd.add("--outfile=${cfg.outfile}")
        cmd.add("--format=${cfg.format}")
        cmd.add("--target=${cfg.target}")

        if (cfg.minify) cmd.add("--minify")

        // JSX handling
        when (cfg.jsx) {
            "automatic" -> {
                cmd.add("--jsx=automatic")
            }
            "transform" -> {
                cmd.add("--jsx=transform")
            }
        }

        // External packages
        for (ext in cfg.external) {
            cmd.add("--external:$ext")
        }

        // Define replacements
        for ((key, value) in cfg.define) {
            cmd.add("--define:$key=$value")
        }

        // Inject files
        for (inject in cfg.inject) {
            cmd.add("--inject:$inject")
        }

        // Loaders
        for ((ext, loader) in cfg.loader) {
            cmd.add("--loader:$ext=$loader")
        }

        if (watch) cmd.add("--watch")

        log.info("Running esbuild: ${cmd.joinToString(" ")}")

        return try {
            val process = ProcessBuilder(cmd)
                .directory(root)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                val size = if (outFile.exists()) outFile.length() else 0
                log.info("Bundle success: ${cfg.outfile} (${size} bytes)")
                BundleResult(
                    success = true,
                    outputPath = outFile.absolutePath,
                    outputSize = size,
                    stderr = stderr.ifEmpty { null }
                )
            } else {
                log.warn("esbuild failed (exit $exitCode): $stderr")
                BundleResult(
                    success = false,
                    error = "esbuild exited with code $exitCode",
                    stderr = stderr.ifEmpty { stdout }
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to run esbuild", e)
            BundleResult(success = false, error = "Failed to run esbuild: ${e.message}")
        }
    }

    /**
     * Bundle and return the output as a string (for deployment).
     */
    fun bundleToString(config: BundleConfig? = null): Pair<BundleResult, String?> {
        val result = bundle(config)
        if (!result.success || result.outputPath == null) return result to null
        return try {
            val content = File(result.outputPath).readText()
            result to content
        } catch (e: Exception) {
            BundleResult(success = false, error = "Failed to read bundle output: ${e.message}") to null
        }
    }

    /**
     * Install esbuild via npm in the project.
     */
    fun installEsbuild(): BundleResult {
        val basePath = project.basePath
            ?: return BundleResult(success = false, error = "Cannot determine project path")
        val root = File(basePath)

        // Check if package.json exists; if not, init one
        val packageJson = File(root, "package.json")
        if (!packageJson.exists()) {
            return try {
                val process = ProcessBuilder("npm", "init", "-y")
                    .directory(root)
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.bufferedReader().readText()
                process.waitFor()
                installEsbuildPackage(root)
            } catch (e: Exception) {
                BundleResult(success = false, error = "npm init failed: ${e.message}")
            }
        }
        return installEsbuildPackage(root)
    }

    private fun installEsbuildPackage(root: File): BundleResult {
        return try {
            val process = ProcessBuilder("npm", "install", "--save-dev", "esbuild")
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                BundleResult(success = true, error = null)
            } else {
                BundleResult(success = false, error = "npm install failed:\n$output")
            }
        } catch (e: Exception) {
            BundleResult(success = false, error = "npm install failed: ${e.message}")
        }
    }

    companion object {
        fun getInstance(project: Project): BundleService =
            project.getService(BundleService::class.java)
    }
}
