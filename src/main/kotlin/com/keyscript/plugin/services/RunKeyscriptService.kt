package com.keyscript.plugin.services

import com.keyscript.plugin.KeyscriptJson
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.io.FileUtil
import com.keyscript.plugin.settings.KeyscriptSettings
import com.keyscript.plugin.preview.KeyscriptPreviewFileEditor
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Paths
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrNull

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

        // Ensure project path is always set on the proxy before building the URL
        val proxyService = ProxyServerService.getInstance(project)
        val proxyBase = proxyService.getProxyBaseUrl()
        val projectDir = project.basePath ?: ""
        if (proxyService.activeProjectPath != projectDir) {
            proxyService.activeProjectPath = projectDir
            log.info("preparePreview: updated activeProjectPath to '$projectDir'")
        }
        log.info("preparePreview: scriptPath=$scriptPath, activeProjectPath=${proxyService.activeProjectPath}, projectDir=$projectDir")
        val params = ScriptParameterService.getInstance(project)
        val instance = params.instance.ifEmpty { KeyscriptSettings.getInstance().getDefaultInstance() }

        return try {
            val jsonParams = KeyscriptJson.mapper.writeValueAsString(params.getScriptParameters())
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

            val debugSuffix = if (params.debugMode) "&debug=true" else ""
            val runUrl = "$proxyBase/$instance/Keyscript_IDE/RunScript" +
                "?scriptPath=${URLEncoder.encode(scriptPath, "UTF-8")}" +
                "&scriptParametersId=${URLEncoder.encode(paramsId, "UTF-8")}" +
                debugSuffix

            log.info("RunScript URL: $runUrl")
            RunResult(success = true, url = runUrl)
        } catch (e: Exception) {
            RunResult(success = false, error = "Run failed: ${e.message}")
        }
    }

    suspend fun runScript(scriptFile: VirtualFile): RunResult {
        if (!KeyscriptFileSupport.isKeyscriptFile(scriptFile, project)) {
            return RunResult(
                success = false,
                error = "This file is not a Keyscript file. Enable Keyscript in Settings > Languages & Frameworks > Keyscript IDE, or add ${KeyscriptFileSupport.markerHint()} near the top of the file."
            )
        }

        // For bundled projects: resolve to the pre-built bundle output instead of the raw source
        val scriptPath = resolveBundleOutputPath(scriptFile) ?: run {
            primePreviewOverride(scriptFile)
            resolveScriptPath(project, scriptFile)
        }

        val result = preparePreview(scriptPath)
        if (result.success && result.url != null) {
            showPreview(scriptFile, result.url)
            // Advance onboarding milestone
            com.keyscript.plugin.onboarding.OnboardingStateService.getInstance(project).completedFirstRun = true
        }
        return result
    }

    suspend fun runScript(scriptPath: String): RunResult {
        val scriptFile = findVirtualFile(scriptPath)
        if (scriptFile != null && !KeyscriptFileSupport.isKeyscriptFile(scriptFile, project)) {
            return RunResult(
                success = false,
                error = "This file is not a Keyscript file. Enable Keyscript in Settings > Languages & Frameworks > Keyscript IDE, or add ${KeyscriptFileSupport.markerHint()} near the top of the file."
            )
        }

        val resolvedPath = if (scriptFile != null) {
            resolveBundleOutputPath(scriptFile) ?: run {
                primePreviewOverride(scriptFile)
                scriptPath
            }
        } else {
            scriptPath
        }

        val result = preparePreview(resolvedPath)
        if (result.success && result.url != null) {
            scriptFile?.let { showPreview(it, result.url) }
        }
        return result
    }

    /**
     * If the script is inside a project with keyscript.bundle.json,
     * auto-start esbuild --watch if needed, then return the bundle output path
     * relative to the project root. Returns null for non-bundled projects.
     */
    private fun resolveBundleOutputPath(scriptFile: VirtualFile): String? {
        val bundleService = BundleService.getInstance(project)
        val diskFile = java.io.File(scriptFile.path)
        val bundleRoot = bundleService.findBundleRootFor(diskFile) ?: return null
        val config = bundleService.readConfigFrom(bundleRoot) ?: return null

        // Auto-start esbuild --watch if not already running
        val watchService = BundleWatchService.getInstance(project)
        val outputFile = java.io.File(bundleRoot, config.outfile)
        if (!outputFile.exists() || !watchService.isWatching(bundleRoot)) {
            log.info("Starting esbuild watch for bundled project at ${bundleRoot.absolutePath}")
            if (!watchService.ensureWatching(bundleRoot)) {
                log.warn("Failed to start esbuild watch — trying one-shot bundle")
                // Fallback: try a one-shot bundle
                val result = bundleService.bundle(config = config, bundleRoot = bundleRoot)
                if (!result.success) {
                    log.warn("Bundle failed: ${result.error}")
                    return null
                }
            }
        }

        if (!outputFile.exists()) {
            log.warn("Bundle output not found after watch/build: ${outputFile.absolutePath}")
            return null
        }

        // Compute path relative to project root
        val projectRootPath = project.guessProjectDir()?.path ?: project.basePath ?: ""
        return if (projectRootPath.isNotEmpty()) {
            val relative = Paths.get(outputFile.absolutePath).relativeToOrNull(Paths.get(projectRootPath))
            if (relative != null) {
                FileUtil.toSystemIndependentName(relative.pathString)
            } else {
                FileUtil.toSystemIndependentName(config.outfile)
            }
        } else {
            FileUtil.toSystemIndependentName(config.outfile)
        }
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
