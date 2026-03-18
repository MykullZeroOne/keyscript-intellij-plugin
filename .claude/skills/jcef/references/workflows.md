# JCEF Workflows Reference

## Contents
- Adding a new browser action to the split editor toolbar
- Wiring a new preview trigger
- Debugging a blank preview
- Adding JavaScript-to-Kotlin messaging

---

## Adding a New Browser Action to the Split Editor Toolbar

The split editor exposes custom toolbar actions alongside IntelliJ's default text editor actions.

```kotlin
// In KeyscriptSplitEditor — add to the existing action list
private fun createPreviewActions(): List<AnAction> = listOf(
    ReloadPreviewAction(previewComponent),
    OpenPreviewInBrowserAction(previewComponent),
    MyNewAction(previewComponent)   // add here
)
```

Define the action:

```kotlin
class MyNewAction(private val preview: KeyscryptPreviewComponent) : AnAction(
    "My Action",
    "Description shown in tooltip",
    AllIcons.General.SomeIcon
) {
    override fun actionPerformed(e: AnActionEvent) {
        preview.doSomething()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = preview.isBrowserReady()
    }
}
```

Checklist:
- [ ] Add action class in `preview/` package
- [ ] Add instance to `createPreviewActions()` in `KeyscriptSplitEditor`
- [ ] Override `update()` to disable the action when browser is null
- [ ] Build and run `./gradlew runIde` to verify icon appears in preview toolbar

---

## Wiring a New Preview Trigger

When you want to reload the preview from outside the editor (e.g., from a toolbar action or service):

1. Get the `KeyscriptPreviewFileEditor` for the active file
2. Call through to `KeyscriptPreviewComponent`

```kotlin
// From any action with access to Project
fun reloadActivePreview(project: Project) {
    val editor = FileEditorManager.getInstance(project).selectedEditors
        .filterIsInstance<TextEditorWithPreview>()
        .firstOrNull() ?: return

    val previewEditor = editor.previewEditor as? KeyscriptPreviewFileEditor ?: return
    previewEditor.previewComponent.reload()
}
```

The proxy must be started before the preview URL is valid. Check via `ProxyServerService`:

```kotlin
val proxy = project.getService(ProxyServerService::class.java)
proxy.ensureStarted()   // no-op if already running
val url = proxy.buildPreviewUrl(scriptPath, instanceName)
previewComponent.loadUrl(url, proxy.ssoSessionId)
```

See the **ktor** skill for proxy startup and URL construction details.

---

## Debugging a Blank Preview

Work through this checklist top-to-bottom before assuming it's a JCEF bug:

- [ ] `JBCefApp.isSupported()` returns `true` — run in sandbox IDE, not headless
- [ ] Proxy is running: `ProxyServerService.isRunning` is `true`
- [ ] `JSESSIONID` is non-null: `ProxyServerService.ssoSessionId` has a value
- [ ] Cookie was injected **before** `loadURL()` was called (order is critical)
- [ ] Proxy URL resolves: open `http://localhost:{proxyPort}/{instance}/Keyscript_IDE/RunScript?...` in an external browser
- [ ] Keystone server is reachable from the IDE machine (check `keystoneServer` in settings)
- [ ] Check Diagnostics panel Console tab for browser JS errors
- [ ] Check IDE log (`Help > Show Log`) for CEF-level errors

Add temporary console handler to surface hidden errors:

```kotlin
b.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
    override fun onConsoleMessage(browser: CefBrowser, level: CefSettings.LogSeverity,
                                  message: String, source: String, line: Int): Boolean {
        LOG.warn("CEF [$level] $message @ $source:$line")
        return false
    }
}, b.cefBrowser)
```

---

## Adding JavaScript-to-Kotlin Messaging

Use `JBCefJSQuery` when browser-side JavaScript needs to call back into Kotlin (e.g., reporting script completion, sending telemetry).

```kotlin
// Create the query handler once, tied to browser lifetime
val jsQuery = JBCefJSQuery.create(browser)

jsQuery.addHandler { result ->
    LOG.info("Got message from browser: $result")
    null  // return null = no error response to JS
}

// Inject the query function into the page after load
b.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
    override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        if (frame.isMain) {
            val js = jsQuery.inject("window.__keyscriptCallback")
            b.cefBrowser.executeJavaScript(js, b.cefBrowser.url, 0)
        }
    }
}, b.cefBrowser)
```

On the JavaScript side (inside the script running in the preview):

```javascript
// Calls Kotlin handler with the given value
window.__keyscriptCallback("hello from script");
```

Dispose `jsQuery` when the browser is disposed:

```kotlin
override fun dispose() {
    jsQuery.dispose()
    browser?.dispose()
}
```

### WARNING: JBCefJSQuery is tied to a specific browser instance

NEVER share a `JBCefJSQuery` between multiple `JBCefBrowser` instances. The query is registered with one browser's JavaScript context. Using it with another browser causes silent no-ops or native crashes.
