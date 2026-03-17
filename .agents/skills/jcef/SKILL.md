Created 3 files at `.Codex/skills/jcef/`:

- **SKILL.md** — quick reference covering browser creation, URL loading with cookie injection, console capture, and live reload with debounce
- **references/patterns.md** — detailed patterns for browser lifecycle, cookie injection order, console capture, split editor integration, and three WARNING anti-patterns (wrong thread creation, missing dispose, using `cefBrowser.loadURL()` directly)
- **references/workflows.md** — step-by-step workflows for adding toolbar actions, wiring new preview triggers, debugging a blank preview (with a top-to-bottom checklist), and adding JS-to-Kotlin messaging via `JBCefJSQuery`