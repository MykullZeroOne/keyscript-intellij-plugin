package com.keyscript.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Detects whether the current project is a Keyscript project.
 * A project is considered a Keyscript project if any of these are true:
 *   - Contains keyscript.bundle.json at the project root
 *   - Contains a .keyscript marker file at the project root
 *   - Contains any *.keyscript.js file in the project tree (shallow scan)
 *   - Contains any .js file with @keyscript marker in the first 5 lines
 *
 * Results are cached per project and can be refreshed.
 */
@Service(Service.Level.PROJECT)
class KeyscriptProjectDetector(private val project: Project) {

    @Volatile
    private var cachedResult: Boolean? = null

    val isKeyscriptProject: Boolean
        get() {
            cachedResult?.let { return it }
            val result = detect()
            cachedResult = result
            return result
        }

    fun refresh() {
        cachedResult = null
    }

    private fun detect(): Boolean {
        val basePath = project.basePath ?: return false
        val root = File(basePath)
        if (!root.isDirectory) return false

        // Fast checks: marker files at project root
        if (File(root, "keyscript.bundle.json").exists()) return true
        if (File(root, ".keyscript").exists()) return true

        // Scan up to 2 levels deep for *.keyscript.js or @keyscript marker
        return scanForKeyscriptFiles(root, maxDepth = 2)
    }

    private fun scanForKeyscriptFiles(dir: File, maxDepth: Int, currentDepth: Int = 0): Boolean {
        if (currentDepth > maxDepth) return false
        val files = dir.listFiles() ?: return false
        for (file in files) {
            if (file.isFile && file.extension == "js") {
                if (file.name.endsWith(".keyscript.js", ignoreCase = true)) return true
                if (hasKeyscriptMarker(file)) return true
            }
            if (file.isDirectory && !file.name.startsWith(".") && file.name != "node_modules") {
                if (scanForKeyscriptFiles(file, maxDepth, currentDepth + 1)) return true
            }
        }
        return false
    }

    private fun hasKeyscriptMarker(file: File): Boolean {
        return try {
            file.bufferedReader().use { reader ->
                repeat(5) {
                    val line = reader.readLine() ?: return false
                    if (line.contains(KeyscriptFileSupport.MARKER)) return true
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun getInstance(project: Project): KeyscriptProjectDetector =
            project.getService(KeyscriptProjectDetector::class.java)

        /** Quick check without requiring the service — for use in static contexts like isApplicable. */
        fun isKeyscriptProject(project: Project): Boolean =
            getInstance(project).isKeyscriptProject
    }
}
