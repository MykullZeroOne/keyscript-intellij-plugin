# Engagement & Adoption: Repeat-Use Journeys

Once activated, users return to two tight loops: the **edit-run-preview loop** and the
**edit-deploy loop**. This document maps those loops, identifies the friction points
that interrupt them, and shows concrete service-level fixes.

## The Edit-Run-Preview Loop

This is the primary daily workflow. Each iteration must complete in under 3 seconds to
feel responsive.

```
[Edit .keyscript.js]
      |
      v
[Press gutter icon or Shift+F10]
      |
      v
[RunKeyscriptService.runScript(scriptFile)]
      |
      v (guard: isKeyscriptFile check)
[primePreviewOverride() — pushes document text into ProxyServerService cache]
      |
      v
[preparePreview(scriptPath) — POST /SessionStore via proxy]
      |
      v (builds run URL with scriptParametersId)
[showPreview(file, url)]
      |
      v
[JCEF loads URL → proxy intercepts → injects JSESSIONID → forwards to Keystone]
      |
      v
[Script output rendered in preview panel]
```

### Friction: stale preview after code edit

`primePreviewOverride` pushes the current document text to the proxy cache. But if the
editor has unsaved changes and the user runs, `FileDocumentManager.getInstance().getDocument()`
returns the in-memory version — which is correct. The issue is when the proxy cache is
populated but the `SessionStore` POST fails, leaving the JCEF panel with the previous
run's URL loaded. There is no visual indication that the new run failed mid-way.

**Fix:** Clear the JCEF panel to a loading state before the `preparePreview()` call,
then only populate it on success.

```kotlin
// In RunKeyscriptService.runScript(scriptFile):
suspend fun runScript(scriptFile: VirtualFile): RunResult {
    if (!KeyscryptFileSupport.isKeyscriptFile(scriptFile)) { ... }

    // Show "loading" in preview immediately so user knows something is happening
    showLoadingState(scriptFile)

    primePreviewOverride(scriptFile)
    val scriptPath = resolveScriptPath(project, scriptFile)
    val result = preparePreview(scriptPath)
    if (result.success && result.url != null) {
        showPreview(scriptFile, result.url)
    } else {
        showErrorState(scriptFile, result.error ?: "Run failed")
    }
    return result
}
```

### Friction: gutter icon appears on the wrong element

`KeyscryptRunLineMarkerContributor` checks `element.parent !is PsiFile` and
`file.firstChild !== element`. In some JS parse states, especially when the file starts
with a BOM or shebang, `firstChild` is not the first leaf in the user-visible sense.
The icon silently disappears.

**WARNING:** Do not relax the `firstChild` check to show the icon on every top-level
element. That causes one icon per `var`/`function` declaration and drowns the gutter.
The fix is to walk to the first non-whitespace leaf:

```kotlin
override fun getInfo(element: PsiElement): Info? {
    if (element.parent !is PsiFile) return null
    val file = element.containingFile ?: return null
    // Walk past BOM / whitespace to first meaningful leaf
    val firstMeaningful = file.firstChild?.let { child ->
        var cur: PsiElement = child
        while (cur is PsiWhiteSpace || cur.text.isBlank()) {
            cur = cur.nextSibling ?: break
        }
        cur
    }
    if (firstMeaningful !== element) return null
    val virtualFile = file.virtualFile ?: return null
    if (!KeyscryptFileSupport.isKeyscriptFile(virtualFile)) return null
    val actions = ExecutorAction.getActions(0)
    return Info(AllIcons.RunConfigurations.TestState.Run, actions) { "Run Keyscript" }
}
```

## The Edit-Deploy Loop

```
[Edit .keyscript.js]
      |
      v
[Trigger DeployAction — right-click menu or toolbar]
      |
      v (checks session.isLoggedIn)
[DeploymentService.deployNew() or deployUpdate()]
      |
      v
[postToKeystone() — direct POST to Keystone API, bypasses proxy]
      |
      v
[parseDeployResponse() — extracts serial]
      |
      v
[NotificationGroupManager balloon: "Deployed: serial #NNNN"]
```

### Friction: deploy silently uses wrong instance

`DeploymentService.getKeystoneUrl()` calls:

```kotlin
val instance = ScriptParameterService.getInstance(project).instance.ifEmpty {
    settings.getDefaultInstance()
}
```

If the user last ran a script on "Test" but wants to deploy to "Development", the
deploy goes to the wrong instance without any prompt. The instance comes from
`ScriptParameterService`, which is set via the Workspace panel Run Options tab — but
most users do not connect these two controls.

**Fix:** Show the target instance in the deploy confirmation dialog.

```kotlin
// In DeployAction.actionPerformed(), before calling DeploymentService:
val targetInstance = ScriptParameterService.getInstance(project).instance
    .ifEmpty { KeyscryptSettings.getInstance().getDefaultInstance() }

val confirmed = Messages.showOkCancelDialog(
    project,
    "Deploy '${file.name}' to Keystone ($targetInstance)?",
    "Deploy Script",
    "Deploy",
    "Cancel",
    Messages.getQuestionIcon()
)
if (confirmed != Messages.OK) return
```

### Friction: deploy errors surface raw JSON exception messages

`parseDeployResponse` extracts `exception.message` from the Keystone response, but
these messages are often Keystone internals like `"DBMS ERROR: duplicate key"`.
The user sees a raw error with no actionable guidance.

**Fix:** Classify known error codes and return human-readable messages.

```kotlin
private fun friendlyError(message: String): String = when {
    message.contains("duplicate key", ignoreCase = true) ->
        "A script with this name already exists. Use 'Update' instead of 'Deploy New'."
    message.contains("session", ignoreCase = true) && message.contains("invalid") ->
        "Session expired. Please login again (status bar widget)."
    message.contains("permission", ignoreCase = true) ->
        "Permission denied. Your account may not have deploy access on this instance."
    else -> message
}
```

## Engagement Anti-Patterns to Avoid

**WARNING: Never auto-run on save.** Adding a document listener that triggers
`RunKeyscriptService.runScript()` on every save creates a spammy UX and hammers the
Keystone server. Scripts may have side effects. The user must always initiate the run
explicitly via the gutter icon or run configuration.

**WARNING: Never auto-deploy on run.** The run and deploy actions are intentionally
separate because run executes the local override (via proxy cache) while deploy
commits source to the SCRIPT table. Merging them removes the ability to test before
committing.

## Adoption Metrics to Track (Conceptual)

These are not wired yet, but they are the right signals to instrument:

| Event | Where to emit | Why it matters |
|-------|--------------|----------------|
| `run.success` | `RunKeyscriptService.runScript()` on success | Core loop health |
| `run.failure.not_logged_in` | guard check in `runScript()` | Activation gap |
| `run.failure.proxy_error` | `preparePreview()` catch block | Infrastructure reliability |
| `deploy.success` | `executeDeployment()` on success | Feature adoption |
| `deploy.failure.session_expired` | `postToKeystone()` 401 path | Session reliability |
| `login.success` | `AuthenticationService.login()` on success | Auth funnel |
| `login.failure` | `AuthenticationService.login()` on failure | Auth friction |
