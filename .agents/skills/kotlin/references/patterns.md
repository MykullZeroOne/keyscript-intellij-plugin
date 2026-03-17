# Kotlin Patterns Reference

## Contents
- Service Lifecycle Pattern
- Listener / Observer Pattern
- Async Operations
- Coroutines vs Platform Threading
- Null Safety and Elvis
- Anti-Patterns

---

## Service Lifecycle Pattern

Every service implements `Disposable`. Resources registered in `Disposer.register()` or via `project.messageBus` subscriptions must be released in `dispose()`. Forgetting this leaks memory across project open/close cycles.

```kotlin
@Service(Service.Level.PROJECT)
class SessionService(private val project: Project) : Disposable {
    private val heartbeatTimer = Timer(true) // daemon timer
    private val _listeners = CopyOnWriteArrayList<SessionListener>()

    init {
        startHeartbeat()
    }

    override fun dispose() {
        heartbeatTimer.cancel()
        _listeners.clear()
    }
}
```

**Checklist for new services:**
- [ ] Annotate with `@Service(Level.PROJECT)` or `@Service(Level.APP)`
- [ ] Constructor takes `Project` for project-scoped, no-arg for app-scoped
- [ ] Implements `Disposable`
- [ ] All background threads/timers cancelled in `dispose()`
- [ ] Registered in `plugin.xml` under `<projectService>` or `<applicationService>`

---

## Listener / Observer Pattern

This codebase uses a manual listener pattern (not IntelliJ message bus) for cross-service notifications. `CopyOnWriteArrayList` is mandatory — listeners are iterated on arbitrary threads while UI threads may add/remove concurrently.

```kotlin
// GOOD — thread-safe, avoids ConcurrentModificationException
private val _listeners = CopyOnWriteArrayList<(LoginState) -> Unit>()

fun addListener(listener: (LoginState) -> Unit) { _listeners.add(listener) }
fun removeListener(listener: (LoginState) -> Unit) { _listeners.remove(listener) }

private fun notifyStateChanged(state: LoginState) {
    _listeners.forEach { it(state) }
}
```

```kotlin
// BAD — ArrayList is not thread-safe for concurrent iteration + modification
private val listeners = ArrayList<(LoginState) -> Unit>()
```

UI components subscribe in their init block and unsubscribe when disposed:

```kotlin
class SessionPanel(project: Project) : JPanel(), Disposable {
    init {
        val session = project.service<SessionService>()
        val listener: (LoginState) -> Unit = { state -> updateUI(state) }
        session.addListener(listener)
        Disposer.register(this) { session.removeListener(listener) }
    }
}
```

---

## Async Operations

NEVER perform I/O or network calls on the Event Dispatch Thread (EDT). The IDE freezes and users notice immediately.

```kotlin
// GOOD — pool thread for work, EDT for UI update
ApplicationManager.getApplication().executeOnPooledThread {
    val result = runCatching { apiClient.deploy(payload) }
    ApplicationManager.getApplication().invokeLater {
        result.fold(
            onSuccess = { showSuccess(it) },
            onFailure = { LOG.warn("Deploy failed", it) }
        )
    }
}
```

```kotlin
// BAD — blocks EDT, freezes the entire IDE
fun onDeployClick() {
    val result = apiClient.deploy(payload) // network call on EDT!
    showSuccess(result)
}
```

For Ktor client calls inside services (not on EDT already), `runBlocking` is acceptable but use it carefully — it blocks the calling thread:

```kotlin
// Acceptable in a pooled thread context
private fun fetchSessionStatus(): Boolean = runBlocking {
    val response = httpClient.get("$apiUrl/heartbeat")
    response.status.isSuccess()
}
```

---

## Coroutines vs Platform Threading

Ktor uses coroutines internally. Outside of Ktor route/client code, prefer `executeOnPooledThread` + `invokeLater` over launching coroutines — it avoids coroutine scope management and aligns with IntelliJ's own async model.

```kotlin
// Prefer this in services
ApplicationManager.getApplication().executeOnPooledThread { /* work */ }

// Use this for Ktor client calls (they're already in coroutine context)
suspend fun callApi(): String = httpClient.get(url).bodyAsText()
```

---

## Null Safety and Elvis

Kotlin null safety prevents NPE, but IntelliJ Platform APIs return `null` heavily (Java interop). Use `?.let`, `?:`, and early returns rather than `!!`.

```kotlin
// GOOD — safe unwrap with default
val instance = settings.getDefaultInstance() ?: "Development"

// GOOD — short-circuit on null
val sessionId = sessionService.getSessionId() ?: return@executeOnPooledThread

// BAD — NPE at runtime if session is null
val sessionId = sessionService.getSessionId()!!
```

`!!` is a runtime crash waiting to happen. Use it only when the null case is a programming error that should fail fast during development.

---

## Anti-Patterns

### WARNING: Calling `service<T>()` from a Disposable After Disposal

**The Problem:**
```kotlin
// BAD — service<T>() may throw if called after project is disposed
override fun dispose() {
    project.service<SessionService>().clearSession() // crash
}
```

**Why This Breaks:** IntelliJ disposes services in an unspecified order. The project may already be disposed or the target service already torn down.

**The Fix:**
```kotlin
override fun dispose() {
    // Manage own state only; don't call out to other services
    _timer.cancel()
    _listeners.clear()
}
```

### WARNING: `lateinit var` for Services Injected via Constructor

**The Problem:**
```kotlin
// BAD — unnecessary, error-prone
@Service(Service.Level.PROJECT)
class WorkspaceUiService : Disposable {
    private lateinit var project: Project
    // project never gets set unless you wire it manually
}
```

**Why This Breaks:** IntelliJ injects the `Project` via constructor. `lateinit var` means you must set it manually and risk `UninitializedPropertyAccessException`.

**The Fix:**
```kotlin
// GOOD — IntelliJ calls this constructor automatically
@Service(Service.Level.PROJECT)
class WorkspaceUiService(private val project: Project) : Disposable
```
