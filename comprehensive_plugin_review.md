# IntelliJ-Platform Plugin Review: Keyscript IDE

## A. Executive Summary
- **Overall Rating**: **Needs Work**
- **Top 5 Issues**:
    1.  **Build Compatibility Mismatch**: The plugin is built against IntelliJ 2024.3 (Build 243) but declares a `since-build` of 253 (2025.1), which will prevent installation on the version it was tested against.
    2.  **Sensitive Information Exposure**: The `sessionId` is explicitly displayed in a UI notification upon successful logon, violating security best practices.
    3.  **Insecure Credential Handling**: Passwords are handled as `String` objects throughout the `LoginDialog`, `LoginAction`, and `KeybridgeClient`, increasing the risk of memory-based credential theft.
    4.  **Suboptimal UI Integration**: Script execution redirects to an external system browser via `BrowserUtil`, causing a jarring context switch for the user.
    5.  **Resource Inefficiency**: `OkHttpClient` instances are created eagerly in client constructors rather than being shared, leading to unnecessary overhead and connection pooling issues.
- **Top 5 Strengths**:
    1.  **Correct Service Scoping**: Appropriate use of Project-level vs. Application-level services.
    2.  **Proper Background Task Usage**: Long-running network operations are correctly wrapped in `Task.Backgroundable`.
    3.  **Modern Persistence**: Effective use of `PersistentStateComponent` for tool window state.
    4.  **Clean Separation of Concerns**: Logic is well-distributed between actions, services, and clients.
    5.  **Strong Extension Point Usage**: Correct registration of tool windows, actions, and configurables.

---

## B. Compliance and Best-Practice Findings

### 1. Build Version Mismatch
- **Severity**: **Critical**
- **Area**: `plugin.xml` / `build.gradle.kts`
- **Problem**: `patchPluginXml` sets `sinceBuild` to "253", but the project builds against `2024.3.3` (Build 243).
- **Why it matters**: Users of the current stable IDE (243) cannot install the plugin, and it may fail on 253 if internal APIs changed.
- **Recommended Fix**: Align `sinceBuild` with the current target platform (243) in `build.gradle.kts`.

### 2. Sensitive Data Leakage in Notifications
- **Severity**: **High**
- **Area**: Security / UI
- **Problem**: `LoginAction.kt` displays the `sessionId` in a balloon notification.
- **Why it matters**: Session IDs are sensitive tokens. Displaying them in the UI or allowing them to be recorded in the Event Log is a security risk.
- **Recommended Fix**: Remove the `sessionId` from the notification message. Use it only internally in `AuthenticationManager`.

### 3. Insecure Password Handling
- **Severity**: **High**
- **Area**: Security
- **Problem**: `LoginDialog` uses `String(passwordField.password)` and passes it as a `String`.
- **Why it matters**: `String` objects are immutable and remain in memory until GC, making them vulnerable to heap dumps.
- **Recommended Fix**: Pass passwords as `CharArray` and use `java.util.Arrays.fill()` to zero them out after use.

### 4. Missing Password Safe Integration
- **Severity**: **Medium**
- **Area**: Security / State
- **Problem**: The plugin does not offer to remember credentials using `com.intellij.credentialStore.PasswordSafe`.
- **Why it matters**: Forcing users to re-enter passwords manually every session degrades UX and often leads to users choosing weaker passwords or storing them in insecure local files.
- **Recommended Fix**: Implement an optional "Remember Password" feature using `PasswordSafe`.

### 5. External Browser Redirect for Execution
- **Severity**: **Medium**
- **Area**: UI/UX
- **Problem**: `RunKeyscriptAction` uses `BrowserUtil.browse(runUrl)` to open the execution interface.
- **Why it matters**: It takes the developer out of their flow. IntelliJ plugins should strive to keep the user inside the IDE.
- **Recommended Fix**: Use a JCEF-based Tool Window or Editor to display the execution URL.

### 6. Inefficient HttpClient Management
- **Severity**: **Low**
- **Area**: Performance
- **Problem**: `KeybridgeClient` and `KeystoneClient` create new `OkHttpClient` instances in their constructors.
- **Why it matters**: `OkHttpClient` is designed to be shared to take advantage of connection pooling and reduce resource consumption.
- **Recommended Fix**: Define a shared `OkHttpClient` in a service or use a singleton pattern.

### 7. Use of `println` for Logging
- **Severity**: **Low**
- **Area**: Architecture / Maintainability
- **Problem**: Multiple classes (`AuthenticationManager`, `LoginAction`, `KeybridgeClient`) use `println` for debugging.
- **Why it matters**: `println` output is not captured by the IDE's logging system and cannot be easily filtered or disabled.
- **Recommended Fix**: Replace with `com.intellij.openapi.diagnostic.Logger`.

---

## C. JetBrains-Native Integration Opportunities
- **JCEF Browser**: Replace external browser calls with an embedded JCEF browser for script execution.
- **Password Safe**: Use the native credential store for managing user passwords.
- **HttpRequests API**: Consider using the platform's `HttpRequests` for simpler networking needs to reduce dependencies.
- **StatusBar Widget**: Display the current Keystone logon status and session validity in the Status Bar for better visibility.

---

## D. Marketplace and Compatibility Risks
- **Plugin Verifier Failure**: The mismatch between build version and `sinceBuild` will likely cause the Plugin Verifier to flag the plugin as incompatible.
- **User Trust**: Displaying session IDs in notifications and hardcoding default usernames ("rev-api-user") can look unprofessional.
- **API Stability**: Targeting Build 253 while building on 243 is risky as platform APIs evolve.

---

## E. Prioritized Remediation Plan
1.  **Immediate Fixes**:
    -   Fix build version alignment in `build.gradle.kts`.
    -   Remove `sessionId` from notifications.
    -   Replace `println` with `Logger`.
2.  **Next Iteration Improvements**:
    -   Refactor password handling to use `CharArray`.
    -   Implement a shared `OkHttpClient` service.
    -   Add `PasswordSafe` support for credentials.
3.  **Nice-to-Have Refinements**:
    -   Integrate JCEF for script execution.
    -   Add a status bar widget for connection status.

---

## F. Final Verdict
The **Keyscript IDE** plugin is **reasonably aligned** with basic platform conventions but **not yet ready for Marketplace release**. The critical build version mismatch and high-severity security issues must be resolved before any broader release.
