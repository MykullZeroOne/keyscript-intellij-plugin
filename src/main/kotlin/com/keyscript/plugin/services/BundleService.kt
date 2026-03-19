package com.keyscript.plugin.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.keyscript.plugin.KeyscriptJson
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrNull

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
    private val mapper = KeyscriptJson.mapper

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
    fun readConfig(): BundleConfig? = readConfigFrom(findBundleRoot())

    /**
     * Read keyscript.bundle.json from a specific directory.
     */
    fun readConfigFrom(bundleRoot: File?): BundleConfig? {
        if (bundleRoot == null) return null
        val configFile = File(bundleRoot, "keyscript.bundle.json")
        if (!configFile.exists()) return null
        return try {
            mapper.readValue(configFile)
        } catch (e: Exception) {
            log.warn("Failed to parse keyscript.bundle.json in ${bundleRoot.absolutePath}", e)
            null
        }
    }

    /**
     * Check if the project has a bundle configuration.
     */
    fun hasBundleConfig(): Boolean = findBundleRoot() != null

    /**
     * Find the nearest directory containing keyscript.bundle.json.
     * Checks project root first, then searches common subdirectories.
     */
    fun findBundleRoot(): File? {
        val basePath = project.basePath ?: return null
        val root = File(basePath)
        if (File(root, "keyscript.bundle.json").exists()) return root
        return null
    }

    /**
     * Find the bundle root by walking up from a script file's directory.
     * This handles cases where the script is in a subdirectory that has its own keyscript.bundle.json.
     */
    fun findBundleRootFor(scriptFile: File): File? {
        val basePath = project.basePath ?: return null
        val projectRoot = File(basePath).canonicalFile
        var dir = scriptFile.parentFile?.canonicalFile
        while (dir != null && dir.path.startsWith(projectRoot.path)) {
            if (File(dir, "keyscript.bundle.json").exists()) return dir
            dir = dir.parentFile
        }
        return null
    }

    /**
     * Locate the esbuild binary relative to a bundle root directory. Checks:
     * 1. Bundle-root-local node_modules/.bin/esbuild
     * 2. Project-root-local node_modules/.bin/esbuild
     * 3. Global esbuild on PATH
     */
    fun findEsbuild(bundleRoot: File? = null): String? {
        val esbuildName = if (SystemInfo.isWindows) "esbuild.cmd" else "esbuild"

        // Check bundle root first
        if (bundleRoot != null) {
            val localBin = File(bundleRoot, "node_modules/.bin/$esbuildName")
            if (localBin.exists() && localBin.canExecute()) return localBin.absolutePath
        }

        // Check project root
        val basePath = project.basePath
        if (basePath != null) {
            val projectBin = File(basePath, "node_modules/.bin/$esbuildName")
            if (projectBin.exists() && projectBin.canExecute()) return projectBin.absolutePath
        }

        // Check PATH
        return try {
            val probeCmd = if (SystemInfo.isWindows) "where" else "which"
            val process = ProcessBuilder(probeCmd, "esbuild")
                .redirectErrorStream(true)
                .start()
            val result = process.inputStream.bufferedReader().readLines().firstOrNull()?.trim()
            process.waitFor()
            if (process.exitValue() == 0 && !result.isNullOrEmpty()) result else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Run esbuild with the project's bundle configuration.
     * @param bundleRoot The directory containing keyscript.bundle.json. If null, uses project root.
     */
    fun bundle(config: BundleConfig? = null, watch: Boolean = false, bundleRoot: File? = null): BundleResult {
        val root = bundleRoot ?: findBundleRoot()
            ?: return BundleResult(success = false, error = "No keyscript.bundle.json found")

        val cfg = config ?: readConfigFrom(root)
            ?: return BundleResult(success = false, error = "No keyscript.bundle.json found in ${root.absolutePath}")

        val esbuild = findEsbuild(root)
            ?: return BundleResult(
                success = false,
                error = "esbuild not found. Install it with: npm install esbuild --save-dev"
            )

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
                val npmCmd = if (SystemInfo.isWindows) listOf("cmd", "/c", "npm") else listOf("npm")
                val process = ProcessBuilder(npmCmd + listOf("init", "-y"))
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
            val npmCmd = if (SystemInfo.isWindows) listOf("cmd", "/c", "npm") else listOf("npm")
            val process = ProcessBuilder(npmCmd + listOf("install", "--save-dev", "esbuild"))
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
