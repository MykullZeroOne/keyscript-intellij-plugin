# Kotlin Types Reference

## Contents
- Data Classes for API Results
- Sealed Classes for State
- Enums
- Settings State Classes
- Type Safety with IntelliJ Java Interop
- Anti-Patterns

---

## Data Classes for API Results

API calls return `data class` wrappers rather than throwing exceptions. This keeps call sites clean and forces explicit handling of failure states.

```kotlin
// Pattern used by KeystoneApiClient and AuthenticationService
data class ApiResult(
    val success: Boolean,
    val data: JsonNode? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false
)

data class LoginResult(
    val success: Boolean,
    val sessionId: String? = null,
    val username: String? = null,
    val error: String? = null
)

// Call site
val result = apiClient.deploy(payload)
if (!result.success) {
    if (result.sessionExpired) handleSessionExpired()
    else LOG.warn("Deploy error: ${result.error}")
    return
}
processData(result.data!!) // safe after success check
```

Always include a `sessionExpired: Boolean` flag on results that hit authenticated endpoints — Keystone returns ambiguous errors when sessions expire and callers need to distinguish expiry from real failures.

---

## Sealed Classes for State

Use sealed classes when a value has a fixed set of mutually exclusive states. Prefer over Boolean flags or stringly-typed status fields.

```kotlin
sealed class LoginState {
    object LoggedOut : LoginState()
    object LoggingIn : LoginState()
    data class LoggedIn(val username: String, val sessionId: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

// Exhaustive when — compiler errors if a state is unhandled
fun updateStatusBar(state: LoginState) = when (state) {
    is LoginState.LoggedOut -> widget.setText("KS: Not Logged In")
    is LoginState.LoggingIn -> widget.setText("KS: Logging In...")
    is LoginState.LoggedIn -> widget.setText("KS: ${state.username}")
    is LoginState.Error -> widget.setText("KS: Error")
}
```

```kotlin
// BAD — Boolean flags that must stay in sync
var isLoggedIn: Boolean = false
var isLoggingIn: Boolean = false
var loginError: String? = null
// What does isLoggedIn=true, isLoggingIn=true mean? Undefined state.
```

---

## Enums

Use enums for fixed sets of string constants — particularly API field names and instance identifiers — to eliminate typos.

```kotlin
enum class DeployOperation(val value: String) {
    INSERT("insert"),
    UPDATE("update"),
    SEARCH("search")
}

// Usage: operation.value instead of a raw string
val payload = mapOf("operation" to DeployOperation.INSERT.value)
```

---

## Settings State Classes

`KeyscryptSettings` uses `PersistentStateComponent` with the settings class as its own state. All fields must be `var` with default values for XML serialization to work correctly.

```kotlin
@Service(Service.Level.APP)
@State(name = "KeyscryptSettings", storages = [Storage("keyscript.xml")])
class KeyscryptSettings : PersistentStateComponent<KeyscryptSettings> {
    // MUST be var with defaults — val fields are not persisted
    var keystoneServer: String = "keystonedev.example.com:8443"
    var proxyPort: Int = 3000
    var supportedInstances: String = "Test,Development"

    // Computed properties are fine — not persisted
    fun getDefaultInstance(): String = supportedInstances.split(",").firstOrNull()?.trim() ?: "Development"

    override fun getState(): KeyscryptSettings = this
    override fun loadState(state: KeyscryptSettings) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        fun getInstance(): KeyscryptSettings = ApplicationManager.getApplication().service()
    }
}
```

```kotlin
// BAD — val fields are ignored by XmlSerializerUtil
var keystoneServer: val = "default" // compile error anyway, but val in state = not persisted
```

---

## Type Safety with IntelliJ Java Interop

IntelliJ Platform is Java-based. Platform APIs return `@Nullable` Java types that Kotlin treats as platform types (`T!`). Access these through safe calls or explicit null checks.

```kotlin
// GOOD — safe access to Java @Nullable return
val project: Project? = DataManager.getInstance().getDataContext(component)
    .getData(CommonDataKeys.PROJECT)
val settings = project?.service<KeyscryptSettings>() ?: return

// BAD — platform types can be null; !! crashes at runtime
val project = DataManager.getInstance().getDataContext(component)
    .getData(CommonDataKeys.PROJECT)!! // NullPointerException in production
```

For `VirtualFile` and `PsiFile` references, always guard against null and check `isValid` before use:

```kotlin
val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
if (!file.isValid) return
```

---

## Anti-Patterns

### WARNING: Mutable State in Data Classes Shared Across Threads

**The Problem:**
```kotlin
// BAD — mutable data class shared as service state
data class SessionState(
    var sessionId: String? = null,
    var username: String? = null,
    var isLoggedIn: Boolean = false
)

// Two threads modify independently — race condition
private var state = SessionState()
fun setSession(id: String) { state.sessionId = id }   // Thread A
fun clearSession() { state.isLoggedIn = false }        // Thread B
```

**Why This Breaks:** Partial updates leave the object in an inconsistent state (e.g., `isLoggedIn=true` but `sessionId=null`).

**The Fix:**
```kotlin
// GOOD — replace the entire state atomically
@Volatile private var sessionId: String? = null
@Volatile private var username: String? = null

fun setSession(id: String, user: String) {
    username = user       // write username first
    sessionId = id        // then sessionId — readers see consistent state or null
    notifyListeners()
}
```

Or use a sealed class (see above) replaced atomically with `@Volatile`.

### WARNING: Using `Any` as Jackson Deserialization Target

**The Problem:**
```kotlin
// BAD — Jackson infers Map<String, Any> for JSON objects
val result: Any = objectMapper.readValue(json)
val field = (result as Map<String, Any>)["field"] as String // unsafe casts
```

**The Fix:**
```kotlin
// GOOD — typed deserialization
data class KeystoneResponse(val status: String, val data: List<Map<String, Any>>)
val result = objectMapper.readValue<KeystoneResponse>(json)

// Or use JsonNode for dynamic structures
val node: JsonNode = objectMapper.readTree(json)
val field = node["field"]?.asText() ?: ""
```

See the **jackson** skill for full deserialization patterns.
