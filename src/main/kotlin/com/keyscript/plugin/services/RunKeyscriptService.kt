package com.keyscript.plugin.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.keyscript.plugin.settings.KeyscriptSettings
import com.keyscript.plugin.preview.KeyscriptPreviewFileEditor
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

@Service(Service.Level.PROJECT)
class RunKeyscriptService(private val project: Project) {
    private val log = Logger.getInstance(RunKeyscriptService::class.java)
    data class RunResult(
        val success: Boolean,
        val url: String? = null,
        val error: String? = null
    )

    suspend fun preparePreview(scriptPath: String): RunResult {
        val session = SessionService.getInstance(project)
        if (!session.isLoggedIn) {
            return RunResult(success = false, error = "Please login to Keystone first (Keyscript > Login)")
        }

        val proxyBase = ProxyServerService.getInstance(project).getProxyBaseUrl()
        val params = ScriptParameterService.getInstance(project)
        val instance = params.instance.ifEmpty { KeyscriptSettings.getInstance().getDefaultInstance() }

        return try {
            val jsonParams = jacksonObjectMapper().writeValueAsString(params.getScriptParameters())
            val formBody = "value=${URLEncoder.encode(jsonParams, "UTF-8")}&id="

            val conn = URI("$proxyBase/SessionStore").toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.outputStream.use { it.write(formBody.toByteArray()) }

            val storeBody = conn.inputStream.bufferedReader().readText()
            log.info("SessionStore response: status=${conn.responseCode}, body=${storeBody.take(300)}")
            val paramsId = extractJsonField(storeBody, "id")
                ?: return RunResult(success = false, error = "Failed to store session parameters")

            val runUrl = "$proxyBase/$instance/Keyscript_IDE/RunScript" +
                "?scriptPath=${URLEncoder.encode(scriptPath, "UTF-8")}" +
                "&scriptParametersId=${URLEncoder.encode(paramsId, "UTF-8")}"

            log.info("RunScript URL: $runUrl")
            RunResult(success = true, url = runUrl)
        } catch (e: Exception) {
            RunResult(success = false, error = "Run failed: ${e.message}")
        }
    }

    suspend fun runScript(scriptFile: VirtualFile): RunResult {
        if (!KeyscriptFileSupport.isKeyscriptFile(scriptFile)) {
            return RunResult(
                success = false,
                error = "This file is not marked for Keyscript preview. Add ${KeyscriptFileSupport.markerHint()} near the top of the file."
            )
        }
        primePreviewOverride(scriptFile)
        val scriptPath = resolveScriptPath(project, scriptFile)
        val result = preparePreview(scriptPath)
        if (result.success && result.url != null) {
            showPreview(scriptFile, result.url)
        }
        return result
    }

    suspend fun runScript(scriptPath: String): RunResult {
        val scriptFile = findVirtualFile(scriptPath)
        if (scriptFile != null && !KeyscriptFileSupport.isKeyscriptFile(scriptFile)) {
            return RunResult(
                success = false,
                error = "This file is not marked for Keyscript preview. Add ${KeyscriptFileSupport.markerHint()} near the top of the file."
            )
        }
        scriptFile?.let(::primePreviewOverride)
        val result = preparePreview(scriptPath)
        if (result.success && result.url != null) {
            scriptFile?.let { showPreview(it, result.url) }
        }
        return result
    }

    companion object {
        fun getInstance(project: Project): RunKeyscriptService =
            project.getService(RunKeyscriptService::class.java)

        fun resolveScriptPath(project: Project, file: VirtualFile): String {
            val baseDir = project.guessProjectDir() ?: return file.name
            return VfsUtilCore.getRelativePath(file, baseDir, '/') ?: file.name
        }
    }

    private fun showPreview(file: VirtualFile, url: String) {
        val editorManager = FileEditorManager.getInstance(project)
        editorManager.openFile(file, true)
        var splitEditor = editorManager.getAllEditors(file)
            .filterIsInstance<TextEditorWithPreview>()
            .firstOrNull()
        if (splitEditor == null) {
            editorManager.closeFile(file)
            editorManager.openFile(file, true)
            splitEditor = editorManager.getAllEditors(file)
                .filterIsInstance<TextEditorWithPreview>()
                .firstOrNull()
        }
        splitEditor?.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)
        (splitEditor?.previewEditor as? KeyscriptPreviewFileEditor)?.loadPreparedUrl(url)
    }

    private fun findVirtualFile(scriptPath: String): VirtualFile? {
        val baseDir = project.guessProjectDir() ?: return null
        return baseDir.findFileByRelativePath(scriptPath)
    }

    fun primePreviewOverride(scriptFile: VirtualFile) {
        val document = FileDocumentManager.getInstance().getDocument(scriptFile) ?: return
        val scriptPath = resolveScriptPath(project, scriptFile)
        ProxyServerService.getInstance(project).setPreviewScriptOverride(scriptPath, document.text)
    }

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = """"$field"\s*:\s*"([^"]+)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }
}
