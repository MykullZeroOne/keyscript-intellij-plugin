package com.keyscript.plugin.preview

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.keyscript.plugin.services.RunKeyscriptService
import javax.swing.JComponent
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.Timer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.keyscript.plugin.services.BundleService
import com.keyscript.plugin.services.BundleWatchService
import com.keyscript.plugin.services.KeyscriptFileSupport
import com.keyscript.plugin.services.PreviewContentService
import com.keyscript.plugin.services.SessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeyscriptSplitEditorProvider : TextEditorWithPreviewProvider(KeyscriptPreviewFileEditorProvider()) {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        KeyscriptFileSupport.isKeyscriptFile(file, project)

    override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
        return KeyscriptSplitEditor(firstEditor, secondEditor as KeyscriptPreviewFileEditor)
    }
}

private class KeyscriptPreviewFileEditorProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        KeyscriptFileSupport.isKeyscriptFile(file, project)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        KeyscriptPreviewFileEditor(project, file)

    override fun getEditorTypeId(): String = "keyscript-preview-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

private class KeyscriptSplitEditor(
    textEditor: TextEditor,
    private val keyscriptPreviewEditor: KeyscriptPreviewFileEditor
) : TextEditorWithPreview(
    textEditor,
    keyscriptPreviewEditor,
    "Keyscript Preview",
    Layout.SHOW_EDITOR,
    false
) {
    override fun createTabActions(): Array<AnAction> {
        return super.createTabActions() + arrayOf(
            ReloadPreviewAction(keyscriptPreviewEditor),
            OpenPreviewInBrowserAction(keyscriptPreviewEditor)
        )
    }
}

private class ReloadPreviewAction(
    private val previewFileEditor: KeyscriptPreviewFileEditor
) : AnAction("Reload Preview", "Reload Keyscript preview", AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
        previewFileEditor.refreshFromCurrentState()
    }
}

private class OpenPreviewInBrowserAction(
    private val previewFileEditor: KeyscriptPreviewFileEditor
) : AnAction("Open in Browser", "Open current Keyscript preview in browser", AllIcons.General.Web) {
    override fun actionPerformed(e: AnActionEvent) {
        previewFileEditor.openInBrowser()
    }
}

class KeyscriptPreviewFileEditor(
    private val project: Project,
    private val file: VirtualFile
) : FileEditor {
    private val userDataHolder = UserDataHolderBase()
    private val propertyChangeSupport = PropertyChangeSupport(this)
    private val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val previewComponent = KeyscriptPreviewComponent(project)
    private val relativePath = RunKeyscriptService.resolveScriptPath(project, file)
    private val fileDocumentManager = FileDocumentManager.getInstance()
    private val reloadTimer: Timer
    private lateinit var sessionListener: () -> Unit
    private var disposed = false

    init {
        val debounceMs = if (BundleService.getInstance(project).hasBundleConfig()) 800 else 300
        reloadTimer = Timer(debounceMs) {
            previewComponent.reloadPreservingState()
        }.apply {
            isRepeats = false
        }

        val multicaster = com.intellij.openapi.editor.EditorFactory.getInstance().eventMulticaster
        multicaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (fileDocumentManager.getFile(event.document) != file) {
                    return
                }

                syncEditorContentOverride()
                if (previewComponent.hasLoadedUrl()) {
                    reloadTimer.restart()
                }
            }
        }, this)

        // Auto-load preview when session becomes available (handles case where
        // editor opens before auto-login completes on startup)
        sessionListener = {
            if (!disposed && !previewComponent.hasLoadedUrl() && SessionService.getInstance(project).isLoggedIn) {
                refreshFromCurrentState()
            }
        }
        SessionService.getInstance(project).addListener(sessionListener)
    }

    override fun getComponent(): JComponent = previewComponent.component

    override fun getPreferredFocusedComponent(): JComponent = previewComponent.component

    override fun getName(): String = "Keyscript Preview"

    override fun setState(state: com.intellij.openapi.fileEditor.FileEditorState) {}

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun <T : Any?> getUserData(key: Key<T>): T? = userDataHolder.getUserData(key)

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
        userDataHolder.putUserData(key, value)
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }

    override fun selectNotify() {
        ensurePreviewLoaded()
    }

    override fun getFile(): VirtualFile = file

    fun ensurePreviewLoaded() {
        if (!previewComponent.hasLoadedUrl()) {
            refreshFromCurrentState()
        }
    }

    fun refreshFromCurrentState() {
        syncEditorContentOverride()
        editorScope.launch {
            val result = withContext(Dispatchers.IO) {
                val scriptPath = resolveBundleOutputPath() ?: relativePath
                RunKeyscriptService.getInstance(project).preparePreview(scriptPath)
            }

            withContext(Dispatchers.Main) {
                if (disposed) return@withContext

                if (result.success && result.url != null) {
                    previewComponent.loadUrl(result.url)
                    showEditorAndPreview()
                } else {
                    previewComponent.showMessage(result.error ?: "Preview unavailable.")
                }
            }
        }
    }

    /**
     * If this file is inside a bundled project, auto-start watcher and return the bundle output path.
     */
    private fun resolveBundleOutputPath(): String? {
        val bundleService = BundleService.getInstance(project)
        val diskFile = java.io.File(file.path)
        val bundleRoot = bundleService.findBundleRootFor(diskFile) ?: return null
        val config = bundleService.readConfigFrom(bundleRoot) ?: return null

        // Auto-start esbuild --watch
        BundleWatchService.getInstance(project).ensureWatching(bundleRoot)

        val outputFile = java.io.File(bundleRoot, config.outfile)
        if (!outputFile.exists()) return null

        val projectRoot = project.basePath ?: return config.outfile
        return if (outputFile.absolutePath.startsWith(projectRoot)) {
            outputFile.absolutePath.removePrefix(projectRoot).removePrefix("/")
        } else {
            config.outfile
        }
    }

    fun loadPreparedUrl(url: String) {
        syncEditorContentOverride()
        previewComponent.loadUrl(url)
        showEditorAndPreview()
    }

    fun openInBrowser() {
        val existingUrl = previewComponent.currentUrl()
        if (existingUrl != null) {
            BrowserUtil.browse(existingUrl)
            return
        }

        syncEditorContentOverride()
        editorScope.launch {
            val result = withContext(Dispatchers.IO) {
                val scriptPath = resolveBundleOutputPath() ?: relativePath
                RunKeyscriptService.getInstance(project).preparePreview(scriptPath)
            }
            withContext(Dispatchers.Main) {
                if (disposed) return@withContext
                if (result.success && result.url != null) {
                    previewComponent.loadUrl(result.url)
                    BrowserUtil.browse(result.url)
                } else {
                    previewComponent.showMessage(result.error ?: "Preview unavailable.")
                }
            }
        }
    }

    override fun dispose() {
        disposed = true
        editorScope.cancel()
        reloadTimer.stop()
        SessionService.getInstance(project).removeListener(sessionListener)
        PreviewContentService.getInstance(project).removeScriptOverride(relativePath)
        previewComponent.dispose()
    }

    private fun syncEditorContentOverride() {
        val document = fileDocumentManager.getDocument(file) ?: return
        PreviewContentService.getInstance(project).setScriptOverride(relativePath, document.text)
    }

    private fun showEditorAndPreview() {
        val editors = FileEditorManager.getInstance(project).getAllEditors(file)
        val splitEditor = editors.filterIsInstance<TextEditorWithPreview>().firstOrNull() ?: return
        splitEditor.setLayout(TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW)
    }
}
