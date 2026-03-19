package com.keyscript.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages an esbuild --watch process for bundled Keyscript projects.
 * Auto-starts when the first preview is requested for a bundled project,
 * keeps running to rebuild on file changes, and stops on project close.
 */
@Service(Service.Level.PROJECT)
class BundleWatchService(private val project: Project, private val serviceScope: CoroutineScope) : Disposable {
    private val log = Logger.getInstance(BundleWatchService::class.java)
    private val watchProcesses = mutableMapOf<String, Process>()
    private val lock = Any()

    /**
     * Ensure a watch process is running for the given bundle root directory.
     * If already running, this is a no-op.
     * @return true if watch is running (or was just started), false on failure
     */
    fun ensureWatching(bundleRoot: File): Boolean {
        val key = bundleRoot.absolutePath
        synchronized(lock) {
            val existing = watchProcesses[key]
            if (existing != null && existing.isAlive) return true

            val bundleService = BundleService.getInstance(project)
            val config = bundleService.readConfigFrom(bundleRoot) ?: run {
                log.warn("No keyscript.bundle.json in $key")
                return false
            }

            val esbuild = bundleService.findEsbuild(bundleRoot) ?: run {
                log.warn("esbuild not found for $key")
                return false
            }

            // Build the watch command
            val cmd = mutableListOf(esbuild, config.entry)
            cmd.add("--bundle")
            cmd.add("--outfile=${config.outfile}")
            cmd.add("--format=${config.format}")
            cmd.add("--target=${config.target}")
            if (config.minify) cmd.add("--minify")
            when (config.jsx) {
                "automatic" -> cmd.add("--jsx=automatic")
                "transform" -> cmd.add("--jsx=transform")
            }
            for (ext in config.external) cmd.add("--external:$ext")
            for ((k, v) in config.define) cmd.add("--define:$k=$v")
            for (inject in config.inject) cmd.add("--inject:$inject")
            for ((ext, loader) in config.loader) cmd.add("--loader:$ext=$loader")
            cmd.add("--watch")

            log.info("Starting esbuild watch in $key: ${cmd.joinToString(" ")}")

            return try {
                val process = ProcessBuilder(cmd)
                    .directory(bundleRoot)
                    .redirectErrorStream(true)
                    .start()

                // Read output in background coroutine to prevent buffer blocking
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            log.info("[esbuild watch] $line")
                        }
                    } catch (_: Exception) {}
                }

                watchProcesses[key] = process
                log.info("esbuild watch started for $key (pid=${process.pid()})")

                // Give esbuild a moment to do the initial build
                Thread.sleep(500)
                true
            } catch (e: Exception) {
                log.warn("Failed to start esbuild watch for $key", e)
                false
            }
        }
    }

    fun isWatching(bundleRoot: File): Boolean {
        val process = watchProcesses[bundleRoot.absolutePath]
        return process != null && process.isAlive
    }

    fun stopWatching(bundleRoot: File) {
        synchronized(lock) {
            val process = watchProcesses.remove(bundleRoot.absolutePath)
            if (process != null && process.isAlive) {
                process.destroy()
                log.info("Stopped esbuild watch for ${bundleRoot.absolutePath}")
            }
        }
    }

    override fun dispose() {
        synchronized(lock) {
            for ((key, process) in watchProcesses) {
                if (process.isAlive) {
                    process.destroy()
                    log.info("Stopped esbuild watch for $key (project closing)")
                }
            }
            watchProcesses.clear()
        }
    }

    companion object {
        fun getInstance(project: Project): BundleWatchService =
            project.getService(BundleWatchService::class.java)
    }
}
