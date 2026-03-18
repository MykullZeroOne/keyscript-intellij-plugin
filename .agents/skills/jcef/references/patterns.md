# JCEF Patterns Reference

## Contents
- Browser lifecycle
- Cookie injection
- Console capture
- Split editor integration
- Anti-patterns

---

## Browser Lifecycle

Always guard creation with `JBCefApp.isSupported()`. JCEF is unavailable in headless environments (CI, remote dev, some Community Edition configs). Failing to check causes a `JBCefApp` initialization exception that crashes the editor tab.

```kotlin
// GOOD — lazy init with availability gate
private var browser: JBCefBrowser? = null

fun ensureBrowser(): JBCefBrowser? {
    if (!JBCefApp.isSupported()) {
        showMessage("Preview unavailable: JCEF not supported")
        return null
    }
    return browser ?: JBCefBrowser().also { b ->
        browser = b
        add(b.component, "browser")
    }
}
```

Dispose explicitly in `FileEditor.dispose()` — IntelliJ does NOT automatically dispose `JBCefBrowser` when the editor closes. Leaked browsers hold OS-level Chromium processes.

```kotlin
override fun dispose() {
    browser?.dispose()
    browser = null
}
```

---

## Cookie Injection

The `JSESSIONID` must be injected into the global `CefCookieManager` before calling `loadURL()`. The cookie must match the domain/path used by the proxy (`http://localhost`).

```kotlin
fun injectSessionCookie(sessionId: String) {
    val mgr = CefCookieManager.getGlobalManager()
    val cookie = CefCookie(
        "JSESSIONID", sessionId,
        "localhost", "/",
        false,   // secure
        false,   // httpOnly
        null, null, false, null
    )
    mgr.setCookie("http://localhost", cookie)
}
```

Call order matters:
1. `injectSessionCookie(sessionId)`
2. `browser.loadURL(proxyUrl)`

If you call `loadURL()` first, the first request flies without the cookie and Keystone rejects it with a 401/redirect.

---

## Console Capture

Attach a `CefDisplayHandlerAdapter` to route browser `console.log` / errors into the Diagnostics panel. Return `false` from `onConsoleMessage` to also allow CEF's default handling (logs to stderr in debug builds).

```kotlin
b.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
    override fun onConsoleMessage(
        browser: CefBrowser,
        level: CefSettings.LogSeverity,
        message: String,
        source: String,
        line: Int
    ): Boolean {
        val prefix = when (level) {
            CefSettings.LogSeverity.LOGSEVERITY_ERROR -> "[ERR]"
            CefSettings.LogSeverity.LOGSEVERITY_WARNING -> "[WARN]"
            else -> "[LOG]"
        }
        networkMonitorService.appendConsole("$prefix $message ($source:$line)")
        return false
    }
}, b.cefBrowser)
```

---

## Split Editor Integration

The split editor uses `TextEditorWithPreviewProvider` as the base. The preview tab lazy-loads via `selectNotify()` — don't load the preview at construction time, as the editor may never be focused.

```kotlin
class KeyscryptSplitEditorProvider : TextEditorWithPreviewProvider(KeyscriptPreviewFileEditorProvider()) {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.name.endsWith(".keyscript.js") ||
               file.name.endsWith(".js") && isKeyscriptFile(file)
    }
}

class KeyscriptPreviewFileEditor(project: Project, file: VirtualFile) : FileEditor {
    private val previewComponent = KeyscryptPreviewComponent(project)
    private var previewLoaded = false

    override fun selectNotify() {
        if (!previewLoaded) {
            previewLoaded = true
            previewComponent.loadCurrentScript()
        }
    }

    override fun dispose() {
        previewComponent.dispose()
    }
}
```

Register in plugin.xml with `PLACE_AFTER_DEFAULT_EDITOR` so the text editor remains primary:

```xml
<fileEditorProvider id="keyscript-split-preview"
                    implementation="com.keyscript.plugin.preview.KeyscriptSplitEditorProvider"/>
```

---

## Anti-Patterns

### WARNING: Creating JBCefBrowser on the wrong thread

**The Problem:**
```kotlin
// BAD — called from a background thread (e.g., inside runAsync)
ApplicationManager.getApplication().executeOnPooledThread {
    browser = JBCefBrowser()  // Swing component created off EDT
}
```

**Why This Breaks:**
Swing components must be created on the EDT. Off-thread creation causes intermittent rendering corruption and `ConcurrentModificationException` inside CEF's peer setup.

**The Fix:**
```kotlin
// GOOD — use invokeLater when creating from background context
ApplicationManager.getApplication().invokeLater {
    browser = JBCefBrowser()
    add(browser!!.component, "browser")
}
```

---

### WARNING: Not disposing the browser

**The Problem:**
```kotlin
// BAD — no dispose() override
class MyPreviewEditor : FileEditor {
    val browser = JBCefBrowser()
    // missing: override fun dispose()
}
```

**Why This Breaks:**
Each `JBCefBrowser` spawns a native Chromium renderer process. Without `browser.dispose()`, closing tabs leaks processes. With dozens of opens/closes in a work session, the machine runs out of file descriptors.

**The Fix:**
```kotlin
override fun dispose() {
    browser?.dispose()
    browser = null
}
```

---

### WARNING: Using `cefBrowser` directly for URL loading

**The Problem:**
```kotlin
// BAD — bypasses JBCefBrowser lifecycle hooks
browser.cefBrowser.loadURL(url)
```

**Why This Breaks:**
`JBCefBrowser` tracks load state, pending navigations, and lifecycle callbacks at the wrapper level. Using `cefBrowser.loadURL()` directly skips these, causing state desync where `JBCefBrowser` thinks no page is loaded while CEF is already rendering.

**The Fix:**
```kotlin
// GOOD — always go through the wrapper for navigation
browser.loadURL(url)

// EXCEPTION: reload() is safe to call directly (no navigation tracking needed)
browser.cefBrowser.reload()
```
