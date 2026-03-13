# Engagement & Adoption Reference

## Contents
- Surface Discovery via Tab Focus
- Lazy Load as Adoption Gate
- Action Availability as Signal
- Result Feedback Loops
- Anti-Patterns

---

## Surface Discovery via Tab Focus

`WorkspaceUiService` provides programmatic tab switching. Use it to guide users to the right panel
after an action completes rather than leaving them to find it themselves.

```kotlin
// After a script run completes, automatically show the console
val uiService = WorkspaceUiService.getInstance(project)
uiService.showDiagnosticsTab(DiagnosticsTab.CONSOLE)
```

```kotlin
// After a deploy succeeds, focus the Network tab to show the HTTP call
uiService.showDiagnosticsTab(DiagnosticsTab.NETWORK)
```

```kotlin
// After login, focus Session tab to confirm identity
uiService.showWorkspaceTab(WorkspaceTab.SESSION)
```

This is low-cost feature discovery: the user sees a panel they might not have opened manually.

---

## Lazy Load as Adoption Gate

`TableBrowserPanel` uses `componentShown` to defer its first API call. This prevents wasted network
requests for users who never open the panel — and creates a natural "loading moment" that confirms
the panel is alive.

```kotlin
// GOOD — the loading state itself teaches users the panel is interactive
private var hasLoaded = false
init {
    addComponentListener(object : ComponentAdapter() {
        override fun componentShown(e: ComponentEvent) {
            if (!hasLoaded) {
                hasLoaded = true
                statusLabel.text = "Loading tables..."
                loadTableList()
            }
        }
    })
}
```

Don't pre-load data at plugin startup. The IDE already has heavy initialization; adding network calls
makes startup feel sluggish and bloats startup metrics.

---

## Action Availability as Signal

Disable actions that can't succeed yet. A greyed-out button with a tooltip explains the dependency
better than any empty state label.

```kotlin
// ScriptOptionsPanel pattern — disable search until a filter is selected
searchButton.isEnabled = filterCombo.selectedItem != null && searchField.text.isNotBlank()

filterCombo.addItemListener { searchButton.isEnabled = it.stateChange == ItemEvent.SELECTED && searchField.text.isNotBlank() }
searchField.document.addDocumentListener(object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) { updateButtonState() }
    override fun removeUpdate(e: DocumentEvent) { updateButtonState() }
    override fun changedUpdate(e: DocumentEvent) { updateButtonState() }
    private fun updateButtonState() {
        searchButton.isEnabled = filterCombo.selectedItem != null && searchField.text.isNotBlank()
    }
})
```

---

## Result Feedback Loops

The `SearchPanel` and `TableBrowserPanel` use a status label at the bottom of the panel to confirm
search completion and count. Always report result counts — silence after a search is interpreted as a
bug.

```kotlin
// GOOD — explicit feedback for all outcomes
when {
    results.isEmpty() -> statusLabel.text = "No results found"
    results.size == 1 -> {
        statusLabel.text = "1 result found"
        // Auto-select single result for the user
        resultTable.setRowSelectionInterval(0, 0)
    }
    else -> statusLabel.text = "${results.size} results found"
}
```

Auto-selecting a single result is a high-value adoption nudge: if only one thing matched, show it
immediately rather than making the user click.

---

## Panel-Level Adoption Checklist

Copy this checklist when adding a new tool window panel:

```
- [ ] Defines all state types (auth-gated, unconfigured, loading, empty-results, error)
- [ ] Loading state disables action buttons
- [ ] Error state shows specific message (not generic "Error occurred")
- [ ] Result count shown after every search/load
- [ ] Single-result auto-selection implemented where applicable
- [ ] Tab focus triggered programmatically after related actions
- [ ] Lazy load deferred to componentShown, not panel init
- [ ] Session listener registered and unregistered on disposal
```

---

## Anti-Patterns

### WARNING: Silent Empty Results

**The Problem:**
```kotlin
// BAD — table clears silently, user can't distinguish "no results" from "search failed"
fun onSearchComplete(results: List<Row>) {
    tableModel.setRows(results)
}
```

**Why This Breaks:** Users run a search, the table empties, and they assume the query is broken.
Support tickets follow.

**The Fix:**
```kotlin
fun onSearchComplete(results: List<Row>) {
    tableModel.setRows(results)
    statusLabel.text = if (results.isEmpty()) "No results found" else "${results.size} result(s) found"
}
```

### WARNING: Eager-Loading in All Panels at Startup

**The Problem:**
```kotlin
// BAD — fires N API calls when the IDE opens, even if user never opens these panels
class MyFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = MyPanel(project)
        panel.loadAllData()  // runs immediately
    }
}
```

**The Fix:** Attach load to `componentShown` or a user-initiated button press.

---

See the **orchestrating-feature-adoption** skill for rollout and nudge strategies.
See the **improving-activation-flow** skill for funnel metrics on tab engagement.
