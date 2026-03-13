# Measurement & Testing Reference

## Contents
- What to Measure in a Plugin Funnel
- Instrumentation Points
- Testing Funnel Copy Changes
- Anti-Patterns

---

## What to Measure in a Plugin Funnel

Plugin funnels lack browser analytics. Conversion data comes from three sources:

| Source | What It Tells You | How to Access |
|--------|------------------|---------------|
| JetBrains Marketplace stats | Installs, page views, ratings | Marketplace vendor portal |
| Plugin telemetry (opt-in) | Feature usage, error rates | Requires explicit instrumentation |
| User feedback | Drop-off reasons | Support channels, issue tracker |

The Keyscript plugin currently has no telemetry instrumentation. Without it, you're flying blind on which funnel stage loses users.

---

## Instrumentation Points

### Where to instrument in the codebase

The five highest-signal events to track:

```kotlin
// 1. Plugin activated for a project (user has a Keyscript project)
// In KeyscryptProjectService — fires on project open
fun onProjectActivated() {
    trackEvent("plugin_activated")
}

// 2. First-time configuration (Keystone server saved)
// In KeyscryptSettingsConfigurable — fires on Apply
fun onSettingsSaved(wasFirstTime: Boolean) {
    if (wasFirstTime) trackEvent("first_configuration_saved")
}

// 3. First login (authentication success)
// In AuthenticationService — fires after successful login
fun onLoginSuccess(isFirstLogin: Boolean) {
    if (isFirstLogin) trackEvent("first_authentication")
}

// 4. First script run
// In RunKeyscryptService — fires on first execution
fun onScriptRun(runCount: Int) {
    if (runCount == 1) trackEvent("first_script_run")
}

// 5. First deploy
// In DeploymentService — fires on successful deploy
fun onDeploySuccess(deployCount: Int) {
    if (deployCount == 1) trackEvent("first_deploy")
}
```

### IntelliJ platform telemetry options

IntelliJ provides `UsageTracker` and `FUSCounterUsageLogger` for internal Marketplace plugins. For this plugin, use application-level persistent state to track local run counts without sending data externally:

```kotlin
// Track run count in persistent state (no external calls)
@Service(Service.Level.APP)
@State(name = "KeyscryptUsageStats", storages = [Storage("keyscrypt-stats.xml")])
class UsageStatsService : PersistentStateComponent<UsageStatsService.State> {
    data class State(
        var totalScriptRuns: Int = 0,
        var totalLogins: Int = 0,
        var firstRunTimestamp: Long = 0
    )

    private var _state = State()
    override fun getState() = _state
    override fun loadState(state: State) { _state = state }

    fun recordScriptRun() {
        _state.totalScriptRuns++
        if (_state.firstRunTimestamp == 0L) {
            _state.firstRunTimestamp = System.currentTimeMillis()
        }
    }
}
```

See the **instrumenting-product-metrics** skill for full telemetry patterns and event naming conventions.

---

## Testing Funnel Copy Changes

Since this is a plugin (not a web app), A/B testing is not practical. Use structured review instead.

### Copy review checklist

Before publishing copy changes to `plugin.xml` or `README.md`:

- [ ] Read the description aloud — does every sentence pass the "so what?" test?
- [ ] Remove any sentence that describes how the tool works rather than what it achieves
- [ ] Verify jargon terms are anchored with a one-clause explanation on first use
- [ ] Check that the first 50 words would make a non-Keystone developer understand the ICP
- [ ] Confirm feature list uses outcome verbs: "see," "inspect," "generate," "run" — not "provides," "supports," "includes"

### Test the settings UI flow

Validate first-run experience by running the sandbox IDE with a clean settings state:

```bash
# Run with fresh sandbox (clears previous plugin state)
./gradlew runIde --rerun-tasks

# Or clear the sandbox manually
rm -rf build/idea-sandbox/config/options/keyscript*.xml
./gradlew runIde
```

Walk through the flow and verify:
1. Status bar shows "KS: Not Logged In" immediately on project open
2. Clicking the widget shows a login dialog (not an error)
3. Settings fields have placeholder text that explains the expected format
4. Saving settings shows a confirmation or the test-connection result

### Validate Marketplace HTML renders correctly

The `<description>` field accepts HTML but Marketplace strips some tags. Test rendering:

```bash
# Check for unclosed tags or unsupported HTML
grep -oP '(?<=<description><!\[CDATA\[).*(?=\]\]>)' \
  src/main/resources/META-INF/plugin.xml | \
  xmllint --html --noout - 2>&1
```

Marketplace supports: `<b>`, `<i>`, `<ul>`, `<li>`, `<p>`, `<br>`, `<a href>`. It strips `<div>`, `<span>`, `<h1>`-`<h6>`, `<img>`.

---

## Anti-Patterns

### WARNING: Measuring installs as the only success metric

Install count from Marketplace doesn't tell you if users reached their first successful script run. A plugin with 1,000 installs and 50 active users has a 95% abandonment rate somewhere in the funnel.

Track the full sequence: install → configure → login → first run → recurring runs.

Without local usage tracking (`UsageStatsService` pattern above), you have no signal after the install event.

### WARNING: Copy changes without baseline

Before rewriting `plugin.xml` or `README.md`, record the current Marketplace page view and install numbers. Without a baseline, you can't attribute any change in installs to the copy update vs. algorithm or timing effects.

Document in CHANGELOG.md or a tracking note:
```markdown
<!-- Before copy rewrite on 2026-03-11 -->
<!-- Marketplace page views last 30d: [X] -->
<!-- Installs last 30d: [Y] -->
```
