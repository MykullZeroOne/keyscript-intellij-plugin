# Roadmap & Experiments Reference

## Contents
- Feature gating via settings
- Experiment pattern using OnboardingStateService
- Validating new feature instrumentation
- Anti-patterns

---

## Feature Gating via Settings

The plugin's primary mechanism for feature gating is `KeyscryptSettings` (application-level)
and `KeyscryptProjectConfigurable` (project-level). These are the correct points to add
feature flags without external infrastructure:

```kotlin
// settings/KeyscryptSettings.kt — add a feature flag field
@State(name = "KeyscriptSettings", storages = [Storage("KeyscriptSettings.xml")])
class KeyscryptSettings : PersistentStateComponent<KeyscryptSettings> {
    // Existing fields...
    var enabledExperimentalDataTools: Boolean = false  // new feature flag
    var enableQueryBuilderBeta: Boolean = false
}
```

Gate feature rendering:

```kotlin
// In tool window factory — guard experimental features
override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val settings = KeyscryptSettings.getInstance()
    val panel = if (settings.enableQueryBuilderBeta) {
        QueryBuilderPanel(project)
    } else {
        EmptyStatePanel("Query Builder", "Coming soon — enable in Settings > Keyscript IDE")
    }
    toolWindow.component.add(panel)
}
```

---

## Experiment Pattern

For A/B-style experiments during development (not for shipped telemetry), use
`OnboardingStateService` to track which variant a user has seen:

```kotlin
// Add variant tracking to OnboardingStateService.State
data class State(
    // ...
    var onboardingVariant: String = "default"  // "default" | "streamlined" | "guided"
)

// Assign variant on first project open (in KeyscryptProjectService)
fun assignOnboardingVariant(project: Project) {
    val onboarding = OnboardingStateService.getInstance(project)
    if (onboarding.onboardingVariant == "default") {
        // Assign based on username hash for stable assignment
        val session = SessionService.getInstance(project)
        onboarding.onboardingVariant = if (session.username.hashCode() % 2 == 0) {
            "streamlined"
        } else {
            "guided"
        }
    }
}
```

Log variant assignment so it appears in `idea.log`:

```kotlin
log.info("Onboarding variant assigned: ${onboarding.onboardingVariant} for ${session.username}")
```

---

## Validating New Feature Instrumentation

Before shipping a new milestone or feature flag, validate instrumentation with this workflow:

1. Call `OnboardingStateService.getInstance(project).resetOnboarding()` in a debug action
2. Open the Workspace tool window — Getting Started checklist should show all 4 steps incomplete
3. Complete each action in order; verify each step checks off
4. Open `KeyscryptOnboarding.xml` in `.idea/` directory to confirm persistence
5. Restart the sandbox IDE (`./gradlew runIde`) — state must survive restart

Validate persistence manually:

```bash
# In your sandbox IDE project dir, confirm state file exists and contains your milestone
cat ~/.config/JetBrains/IdeaIC2025.1/options/KeyscryptOnboarding.xml
# or on macOS:
cat ~/Library/Application\ Support/JetBrains/IdeaIC2025.1/options/KeyscryptOnboarding.xml
```

Expected output should contain your new field with its value:
```xml
<component name="com.keyscript.plugin.onboarding.OnboardingState">
  <option name="completedFirstRun" value="true" />
  <option name="openedDataTools" value="true" />   <!-- your new field -->
</component>
```

---

## Anti-Patterns

### WARNING: Using System Properties as Feature Flags

```kotlin
// BAD — brittle, invisible to users, not persisted
val isBetaEnabled = System.getProperty("keyscript.beta") == "true"
```

**Why This Breaks:**
1. Requires IDE restart to change
2. Not visible or configurable by the user
3. Can't be toggled at runtime for testing

**The Fix:** Use `KeyscryptSettings` — it's persisted, user-configurable via Settings UI,
and readable anywhere via `KeyscryptSettings.getInstance()`.

### WARNING: Rolling Feature Flags Forward Before Instrumentation Validates

NEVER add a feature to `GettingStartedPanel` before the service that fires the milestone
is implemented. Users will see a permanently-incomplete checklist item.

Checklist for shipping a new milestone step:
- [ ] `OnboardingStateService.State` field added
- [ ] Property setter with `notifyListeners()` guard added
- [ ] Service layer fires the milestone on successful action
- [ ] `GettingStartedPanel.rebuildChecklist()` updated with the step
- [ ] `isComplete` logic updated if this is a required activation step
- [ ] Tested with `resetOnboarding()` cycle

See the **kotlin** skill for `PersistentStateComponent` XML serialization details.
