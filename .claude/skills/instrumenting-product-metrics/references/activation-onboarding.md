# Activation & Onboarding Reference

## Contents
- Milestone state model
- Wiring new milestones
- Reading state in UI
- Anti-patterns
- Checklist

---

## Milestone State Model

`OnboardingStateService` persists activation milestones in `KeyscriptOnboarding.xml` via
`PersistentStateComponent`. The `State` data class is the source of truth.

```kotlin
// onboarding/OnboardingStateService.kt — current fields
data class State(
    var shownWelcome: Boolean = false,       // welcome dialog shown
    var configuredServer: Boolean = false,   // proxy + API URL set
    var completedFirstLogin: Boolean = false,
    var completedFirstRun: Boolean = false,
    var completedFirstDeploy: Boolean = false,
    var dismissed: Boolean = false           // user dismissed checklist
)
```

`isComplete` requires configuredServer + completedFirstLogin + completedFirstRun.
First deploy is tracked but NOT required for completion — it's an advanced action.

---

## Wiring New Milestones

Always fire milestones at the service layer (not the UI layer). The UI reacts via listeners.

```kotlin
// services/RunKeyscriptService.kt — correct pattern
val result = preparePreview(scriptPath)
if (result.success && result.url != null) {
    showPreview(scriptFile, result.url)
    OnboardingStateService.getInstance(project).completedFirstRun = true  // fire here
}
```

```kotlin
// services/DeploymentService.kt — correct pattern
val result = parseDeployResponse(responseBody!!)
if (result.success) {
    OnboardingStateService.getInstance(project).completedFirstDeploy = true  // fire here
}
return result
```

The setter on `OnboardingStateService` only fires listeners if the value actually changes,
preventing re-renders on repeated runs:

```kotlin
var completedFirstRun: Boolean
    get() = myState.completedFirstRun
    set(value) {
        if (myState.completedFirstRun != value) {  // guard prevents spurious redraws
            myState.completedFirstRun = value
            notifyListeners()
        }
    }
```

---

## Reading State in UI

`GettingStartedPanel` subscribes to both `OnboardingStateService` and `SessionService`
so it refreshes when either login state or milestone state changes:

```kotlin
// onboarding/GettingStartedPanel.kt
private val stateListener: () -> Unit = { SwingUtilities.invokeLater { rebuildChecklist() } }

init {
    onboarding.addListener(stateListener)
    SessionService.getInstance(project).addListener(stateListener)
    // ...
}

override fun dispose() {
    onboarding.removeListener(stateListener)
    SessionService.getInstance(project).removeListener(stateListener)
}
```

Progress count drives the progress bar:

```kotlin
val completedCount = listOf(
    onboarding.configuredServer,
    onboarding.completedFirstLogin,
    onboarding.completedFirstRun,
    onboarding.completedFirstDeploy
).count { it }
```

---

## Anti-Patterns

### WARNING: Firing Milestones from UI Code

**The Problem:**
```kotlin
// BAD — milestone set in button click handler
okButton.addActionListener {
    doLogin()
    OnboardingStateService.getInstance(project).completedFirstLogin = true
}
```

**Why This Breaks:**
1. If login fails inside `doLogin()`, the milestone still fires — corrupting state
2. UI code can be bypassed (e.g., programmatic login, auto-relogin)
3. Re-login via `SessionService.handleSessionExpired()` will never fire the milestone

**The Fix:**
```kotlin
// GOOD — fire in SessionService.setSession() or AuthenticationService.login()
fun setSession(jsessionId: String, username: String, ...) {
    this.jsessionId = jsessionId
    // ...
    OnboardingStateService.getInstance(project).completedFirstLogin = true
}
```

### WARNING: Not Cleaning Up Listeners

Panels that register listeners without removing them on `dispose()` cause memory leaks
and NPEs when the project is closed. See `GettingStartedPanel.dispose()` for the pattern.

---

## Checklist: Add a New Milestone

Copy this checklist and track progress:
- [ ] Add `Boolean` field to `OnboardingStateService.State` (defaults to `false`)
- [ ] Expose as a property with change-guard and `notifyListeners()`
- [ ] Fire it at the service layer on success (not in UI)
- [ ] Add a step to `GettingStartedPanel.rebuildChecklist()` if user-visible
- [ ] Decide if it should be part of `isComplete` logic
- [ ] Test by running `resetOnboarding()` in debug and completing the action

See the **kotlin** skill for `PersistentStateComponent` patterns.
See the **intellij-platform** skill for `Disposable` and listener lifecycle.
