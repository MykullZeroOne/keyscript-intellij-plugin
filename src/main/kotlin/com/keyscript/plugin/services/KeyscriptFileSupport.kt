package com.keyscript.plugin.services

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets

object KeyscriptFileSupport {
    const val MARKER = "@keyscript"
    private const val NAMED_SUFFIX = ".keyscript.js"
    private const val MAX_PREFIX_BYTES = 2048
    private const val MAX_PREFIX_LINES = 5

    fun isKeyscriptFile(file: VirtualFile): Boolean {
        if (file.isDirectory || file.extension != "js") {
            return false
        }
        if (file.name.endsWith(NAMED_SUFFIX, ignoreCase = true)) {
            return true
        }

        val prefix = readPrefix(file) ?: return false
        return prefix.lineSequence().take(MAX_PREFIX_LINES).any { it.contains(MARKER) }
    }

    fun markerHint(): String = "// $MARKER"

    private fun readPrefix(file: VirtualFile): String? {
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        if (document != null) {
            return document.text.take(MAX_PREFIX_BYTES)
        }

        return runCatching {
            file.inputStream.use { input ->
                val bytes = input.readNBytes(MAX_PREFIX_BYTES)
                String(bytes, StandardCharsets.UTF_8)
            }
        }.getOrNull()
    }
}
