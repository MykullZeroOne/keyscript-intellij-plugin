# Content Copy Reference

## Contents
- Copy voice and tone
- Writing for each surface
- Headline formulas
- Label and microcopy patterns
- Anti-patterns

## Voice and Tone

**Voice:** Direct, technical peer. You're writing for developers who already know Keystone — don't explain what a script is. Respect their time.

**Tone by surface:**
| Surface | Tone | Example phrase |
|---------|------|----------------|
| README hero | Confident, specific | "Write and run Keyscript scripts without leaving IntelliJ" |
| CHANGELOG | Celebratory, benefit-first | "Scripts now deploy in one click" |
| Error messages | Clear, non-alarming, actionable | "Session expired — click to re-login" |
| Settings labels | Neutral, example-driven | "Server (e.g. keystonedev.example.com:8443)" |
| Empty states | Warm, directional | "Open a .keyscript.js file to see run options" |
| Balloon notifications | Brief, outcome-focused | "Logged in to Development. Ready to run." |

## Writing for Each Surface

### README.md

Lead with the problem developers face without the plugin:
```markdown
<!-- GOOD opening -->
Keyscript development has always meant switching to the Electron IDE to run,
preview, and deploy. Keyscript IDE for IntelliJ brings that workflow into
your existing IDE — completions, proxy auth, JCEF preview, and deploy actions
included.
```

Avoid:
```markdown
<!-- BAD opening — feature dump -->
Keyscript IDE is a powerful plugin that provides many features including
code completions, a proxy server, tool windows, and more.
```

### CHANGELOG.md

Use **capability headlines** followed by a single sentence of benefit:

```markdown
## v2.0.0 — Full IDE Rewrite

### Run scripts from the gutter
Play buttons now appear next to every Keyscript entry point.
One click runs the script in the JCEF preview pane.

### Session management is automatic
The plugin handles JSESSIONID injection and re-login — you stay
connected without manual auth refreshes.

### Deploy to Keystone without a browser
Deploy action available from Run menu and gutter. Uses your
active session — no separate login flow.
```

NEVER use passive voice in CHANGELOG:
```markdown
<!-- BAD -->
A bug was fixed where session tokens were not being persisted.

<!-- GOOD -->
Fixed: session tokens now persist across IDE restarts.
```

### Settings Panel Labels

Label copy should answer: *what do I type here and why?*

```kotlin
// KeyscryptSettingsConfigurable.kt equivalent copy

// Server field
labelFor: "Keystone server"
placeholder: "keystonedev.example.com:8443"
helpText: "The hostname and port of your Keystone instance."

// Instances field
labelFor: "Instances"
placeholder: "Development,Test,Production"
helpText: "Comma-separated. Shown in the run options dropdown."

// Proxy port
labelFor: "Local proxy port"
placeholder: "3000"
helpText: "Port for the embedded proxy. Change if 3000 is taken."
```

### Balloon Notifications

Max 12 words for the title. Max 25 words for the body. One action button if needed.

```kotlin
// Login success
title = "Connected to Keystone"
body = "Logged in as ${username} on ${settings.keystoneServer}. Scripts are ready."
action = null  // no action needed on success

// Session expired
title = "Session expired"
body = "Click to re-login to ${settings.keystoneServer}."
action = LoginAction()  // single, clear action

// Deploy success
title = "Script deployed"
body = "${scriptName} deployed to ${instance}."
action = OpenInBrowserAction()  // optional follow-up
```

## Headline Formulas

| Formula | Example |
|---------|---------|
| [Verb] [object] without [pain] | "Run scripts without leaving IntelliJ" |
| [Outcome] in [time/steps] | "Deploy to Keystone in one click" |
| [Tool] for [ICP] | "Keyscript IDE for Keystone developers" |
| [Old way] → [New way] | "From Electron app to native IDE tooling" |

## Anti-Patterns

### WARNING: "We" language in product copy

**The Problem:** "We've added deploy support" or "Our proxy handles auth"

**Why This Breaks:** Sounds like a press release, not a tool. Developers want to know what *they* can do, not what the team built.

**The Fix:** Reframe to the user's capability: "Deploy scripts to Keystone with one click."

### WARNING: Jargon without translation

Internal terms that need plain-language equivalents in user-facing copy:

| Internal term | User-facing equivalent |
|--------------|----------------------|
| JSESSIONID cookie injection | automatic session authentication |
| Ktor CIO proxy | embedded proxy server |
| JCEF split-editor | browser preview panel |
| CR framework completions | Keyscript API completions |

See the **clarifying-market-fit** skill for a full glossary alignment exercise.
