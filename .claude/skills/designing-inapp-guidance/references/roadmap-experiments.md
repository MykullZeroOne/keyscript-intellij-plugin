# Roadmap and Experiments Reference

## Contents
- Feature Flags via PropertiesComponent
- Staged Guidance Rollout
- A/B Copy Testing (Manual)
- Planned Guidance Surfaces
- Anti-Patterns

---

## Feature Flags via PropertiesComponent

There is no feature flag service. Use `PropertiesComponent` with an explicit flag key as a lightweight toggle. Set flags via the IDE's Registry (for internal testing) or hardcode them for beta gating.

```kotlin
// Simple flag check — toggle during development by setting the property
object FeatureFlags {
    private val props get() = PropertiesComponent.getInstance()

    // Set to true in build to enable; false to hide behind flag
    val queryBuilderV2Enabled: Boolean
        get() = props.getBoolean("keyscript.flag.queryBuilderV2", false)

    val diagnosticsAutoOpenEnabled: Boolean
        get() = props.getBoolean("keyscript.flag.diagnosticsAutoOpen", false)
}
```

To flip a flag during development without recompiling, use IntelliJ's built-in Registry at `Help > Find Action > Registry` and add the key there.

## Staged Guidance Rollout

When introducing new guidance (tours, nudges, inline hints) for an existing feature, roll it out in stages to avoid noise for established users.

```kotlin
// Stage 1: Show only to users who haven't yet completed the target action
fun maybeShowQueryBuilderTour(project: Project) {
    val props = PropertiesComponent.getInstance()
    // Only show to users who have logged in (activated) but never used Query Builder
    if (!ActivationEvents.hasLoggedIn()) return
    if (props.getBoolean("keyscript.tour.queryBuilder.shown", false)) return
    props.setValue("keyscript.tour.queryBuilder.shown", true)
    showQueryBuilderIntroNotification(project)
}

private fun showQueryBuilderIntroNotification(project: Project) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Keyscript IDE")
        .createNotification(
            "New: Query Builder",
            "Build Keystone queries visually in <b>Data Tools &gt; Query Builder</b>. " +
            "Select a table, add filters, and preview the XML before executing.",
            NotificationType.INFORMATION
        )
        .addAction(NotificationAction.createSimple("Open Query Builder") {
            project.service<WorkspaceUiService>().showDataToolsQueryBuilder()
        })
        .notify(project)
}
```

## A/B Copy Testing (Manual)

Without an analytics backend, A/B testing means shipping one variant and comparing before/after support questions or user feedback. Document the variant currently live in code comments.

```kotlin
// COPY VARIANT: "KS: Not logged in" vs "KS: Sign in"
// Current variant: "KS: Not logged in" — matches Keystone terminology
// Candidate: "KS: Sign in" — more action-oriented but departs from established patterns
override fun getText(): String = when {
    !settings.isConfigured() -> "KS: Setup needed"
    !session.isLoggedIn      -> "KS: Not logged in"  // VARIANT A (current)
    else                     -> "KS: ${session.username}"
}
```

## Planned Guidance Surfaces

Track guidance work as product items, not just dev tasks. These surfaces are currently missing:

| Surface | Location | Priority | Notes |
|---------|----------|----------|-------|
| First-run balloon | `KeyscryptProjectService` | High | Not yet implemented |
| Post-login nudge | `SessionService` login callback | High | Not yet implemented |
| Settings field hints | `KeyscryptSettingsConfigurable` | Medium | Fields have no inline help |
| Query Builder tour | `QueryBuilderToolWindowFactory` | Medium | Complex UI, needs intro |
| Table Browser column tooltips | `TableBrowserToolWindowFactory` | Low | Column names are technical |
| Network Monitor decode hint | `NetworkToolWindowFactory` | Low | Explain request/response format |

## Anti-Patterns

### WARNING: Removing guidance without a replacement

**The Problem:**
```kotlin
// BAD — deleting a nudge notification because "users complained"
// (removing without understanding whether the complaint is about timing vs content)
```

**Why This Breaks:** "Too many notifications" usually means wrong timing, not wrong message. Before removing guidance, check whether the issue is (a) it fires too early, (b) it fires repeatedly, or (c) the content is genuinely not useful. Fix the root cause — gate it better or condense it — rather than deleting it.

### WARNING: Shipping new tool window panels without a discovery notification

**The Problem:**
```kotlin
// BAD — new panel registered in plugin.xml but no notification on first use
class NewDataPanelFactory : ToolWindowFactory { /* ... */ }
```

**Why This Breaks:** Tool windows are invisible by default unless the user already knows where to look. Every new panel needs at minimum a post-activation nudge tied to the relevant trigger event. See the **orchestrating-feature-adoption** skill for adoption sequencing.
