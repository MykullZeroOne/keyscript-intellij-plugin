# IntelliJ Platform Patterns Reference

## Contents
- Service Declaration & Retrieval
- Listener / Observer Pattern
- Thread Safety Rules
- Settings Persistence
- Plugin.xml Registration
- Anti-Patterns

---

## Service Declaration & Retrieval

Every service must use `@Service` and a `getInstance` companion — never construct with `new` or store in a static field.

```kotlin
// Project-scoped service (one instance per open project)
@Service(Service.Level.PROJECT)
class DeploymentService(private val project: Project) : Disposable {
    private val log = Logger.getInstance(DeploymentService::class.java)

    override fun dispose() { /* cancel any running coroutines/timers */ }

    companion object {
        fun getInstance(project: Project): DeploymentService =
            project.getService(DeploymentService::class.java)
    }
}

// App-scoped service (one instance for the whole IDE process)
@Service(Service.Level.APP)
@State(name = "KeyscryptSettings", storages = [Storage("KeyscryptSettings.xml")])
class KeyscryptSettings : PersistentStateComponent<KeyscryptSettings.State> { ... }
// Retrieve: ApplicationManager.getApplication().getService(KeyscryptSettings::class.java)
// Or via companion: KeyscryptSettings.getInstance()
```

**DO:** Call `dispose()` on resources (timers, coroutine scopes). `Disposable` services are automatically disposed by the platform when the project closes.

**DON'T:** Call `project.getService()` inside `dispose()` — the service container may already be torn down. Cache the reference during initialization instead.

---

## Listener / Observer Pattern

State changes (session login/logout, workspace tab switches) propagate via a thread-safe listener list. Always dispatch to the EDT before touching Swing.

```kotlin
// In SessionService
private val listeners = CopyOnWriteArrayList<() -> Unit>()

fun addListener(listener: () -> Unit) { listeners.add(listener) }
fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

private fun notifyListeners() {
    val notify = Runnable { listeners.forEach { it() } }
    if (SwingUtilities.isEventDispatchThread()) notify.run()
    else SwingUtilities.invokeLater(notify)
}

// Consumer (e.g., status bar widget)
session.addListener {
    // already on EDT — safe to update Swing components
    updateWidgetText(if (session.isLoggedIn) "KS: ${session.username}" else "KS: Not Logged In")
}
```

**DO:** Remove listeners in `dispose()` to avoid memory leaks. Use `removeListener` or store a reference.

**DON'T:** Use raw `ArrayList` — concurrent modification from background threads will throw `ConcurrentModificationException`.

---

## Thread Safety Rules

| Context | Allowed | Forbidden |
|---------|---------|-----------|
| EDT (Swing callbacks, `invokeLater`) | Read/write Swing components | Long I/O, `runBlocking` |
| Background thread | Network calls, file I/O | Direct Swing mutation |
| `AnAction.update()` | Read project state | Any UI mutation |
| `AnAction.actionPerformed()` | Launch background thread | Blocking network call |

```kotlin
// AnAction: always declare BGT for update(), keeps EDT free
override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

override fun update(e: AnActionEvent) {
    // runs on BGT — safe to call services, read files
    val project = e.project ?: run { e.presentation.isEnabledAndVisible = false; return }
    e.presentation.isEnabledAndVisible = KeyscriptProjectDetector.isKeyscriptProject(project)
}

override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    // Kick off work on a named background thread
    Thread({
        val result = runBlocking { apiClient.deploy(file) }
        SwingUtilities.invokeLater { showResult(result) }
    }, "keyscript-deploy").start()
}
```

---

## Settings Persistence

Use `PersistentStateComponent` for any configuration that should survive IDE restarts. The platform serializes the inner `State` data class to XML automatically.

```kotlin
@Service(Service.Level.APP)
@State(
    name = "com.keyscript.plugin.settings.KeyscryptSettings",
    storages = [Storage("KeyscryptSettings.xml")]
)
class KeyscryptSettings : PersistentStateComponent<KeyscryptSettings.State> {
    data class State(
        var proxyPort: Int = 3000,
        var supportedInstances: String = "Test,Development"
    )
    private var myState = State()
    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }
}
```

For **secrets** (passwords, tokens) use `PasswordSafe` — never store them in `PersistentStateComponent`:

```kotlin
// Write
PasswordSafe.instance.set(
    CredentialAttributes(generateServiceName("Keyscript", "KeystoneLogin")),
    Credentials(username, password)
)

// Read
val cred = PasswordSafe.instance.get(
    CredentialAttributes(generateServiceName("Keyscript", "KeystoneLogin"))
)
val password = cred?.getPasswordAsString()
```

---

## Plugin.xml Registration

Every service, tool window, action, and extension point must be declared in `src/main/resources/META-INF/plugin.xml`. The platform **will not discover them via classpath scanning**.

```xml
<!-- Application service -->
<applicationService serviceImplementation="com.keyscript.plugin.settings.KeyscryptSettings"/>

<!-- Project service -->
<projectService serviceImplementation="com.keyscript.plugin.services.MyService"/>

<!-- Startup hook (suspend fun execute()) -->
<postStartupActivity implementation="com.keyscript.plugin.services.MyService$StartupActivity"/>

<!-- Tool window (secondary=true prevents auto-open on startup) -->
<toolWindow id="My Tool" anchor="right" secondary="true"
            factoryClass="com.keyscript.plugin.toolwindow.MyToolWindowFactory"
            icon="AllIcons.General.Web"/>

<!-- Action in a menu group -->
<action id="Keyscript.MyAction" class="com.keyscript.plugin.actions.MyAction"
        text="Do Something"/>
```

**DO:** Use `secondary="true"` on tool windows to prevent them from opening in every project.

**DON'T:** Forget `<depends>com.intellij.modules.platform</depends>` — without it the plugin won't load.

---

## Anti-Patterns

### WARNING: Blocking the EDT

**The Problem:**
```kotlin
// BAD — blocks EDT, freezes the IDE
override fun actionPerformed(e: AnActionEvent) {
    val result = runBlocking { apiClient.login(user, pass) }
    label.text = result.userName
}
```

**Why This Breaks:**
1. The IDE becomes completely unresponsive during the network call.
2. IntelliJ will log a "UI freeze" event and may kill the plugin.
3. Any heartbeat or background task that tries to acquire an EDT lock will deadlock.

**The Fix:**
```kotlin
override fun actionPerformed(e: AnActionEvent) {
    Thread({
        val result = runBlocking { apiClient.login(user, pass) }
        SwingUtilities.invokeLater { label.text = result.userName }
    }, "keyscript-login").start()
}
```

---

### WARNING: Missing getActionUpdateThread()

**The Problem:**
```kotlin
// BAD — update() runs on EDT by default in older SDK, BGT in newer
class MyAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = expensiveCheck()  // blocks EDT
    }
}
```

**Why This Breaks:** IntelliJ 2023.3+ requires actions to explicitly declare their update thread. Missing this causes a deprecation warning and eventual failure.

**The Fix:**
```kotlin
override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
```

---

### WARNING: Accessing Services After Project Disposal

**The Problem:**
```kotlin
// BAD — project may be disposed by the time the callback fires
Timer().schedule(object : TimerTask() {
    override fun run() {
        val svc = project.getService(SessionService::class.java)  // throws
    }
}, 5000)
```

**The Fix:**
```kotlin
// Check before accessing; cancel timer in dispose()
override fun run() {
    if (project.isDisposed) return
    val svc = project.getService(SessionService::class.java) ?: return
    svc.checkSession()
}
```
