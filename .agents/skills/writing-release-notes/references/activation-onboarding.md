# Activation & Onboarding — Release Note Writing Guide

This reference covers how to write release note entries for features that affect the first-run
experience, initial setup, and new-user flows in the Keyscript IDE plugin.

---

## What Counts as Activation/Onboarding Content

Activation content describes what happens between "plugin installed" and "first successful script
run." In Keyscript IDE, that journey has four distinct gates:

1. **Plugin installed** — Tool windows appear only in Keyscript projects (isApplicable gating).
2. **Server configured** — Settings > Keyscript IDE must have a valid Keystone server URL.
3. **Authenticated** — User clicks the status bar widget and logs in via the login dialog.
4. **First run** — Proxy starts lazily; script executes inside the JCEF preview panel.

Release notes should name which gate an improvement affects.

---

## Writing Patterns

### Pattern 1 — Describing a flow improvement

State the before (friction) and the after (improvement), then say which step it affects.

```
Before:  "Projects required manual enable in settings before tool windows appeared."
After:   "Projects containing keyscript.bundle.json, .keyscript, or *.keyscript.js files
          are now detected automatically. Tool windows appear without any manual configuration."
```

Entry form:
```
- **Auto-Detection**: Projects are now recognized automatically if they contain
  `keyscript.bundle.json`, `.keyscript`, or `*.keyscript.js`. No settings change required
  to activate the plugin on existing projects.
```

### Pattern 2 — Describing a new wizard or setup screen

Name the exact menu path. Describe what the user can accomplish, not what the feature is.

```
- **New Project Wizard**: Create a Keyscript project from File > New > Project > Keyscript.
  Choose from four starting templates — Vanilla JS + Keystone, React + Keystone,
  React Standalone, or Blank Script — each with pre-wired folder structure and
  a sample entry point.
```

### Pattern 3 — Describing a first-run surface change

Identify the widget or panel by name. State what it now shows and what action it enables.

```
- **Status Bar Widget**: The "KS: Not Logged In" widget now appears immediately after
  plugin activation. Clicking it opens the login dialog pre-filled with the last-used
  Keystone server URL, reducing setup time on first run.
```

---

## Keyscript Activation Surfaces

### Status Bar Widget (`LoginStatusBarWidget`)

States to describe in release notes:
- `KS: Not Logged In` — plugin active, not authenticated
- `KS: [username]` — authenticated, session valid
- `KS: Session Expired` — credential available, heartbeat failed

When writing entries about the widget, name the exact label text users will see. Example:

```
- **Status Bar**: The widget now displays "KS: Session Expired" rather than silently
  dropping to "KS: Not Logged In" when the Keystone session times out, making it
  clear that saved credentials are still available.
```

### Settings Panel (`Settings > Keyscript IDE`)

Fields users must complete before first use:
- Keystone Server (e.g., `keystonedev.example.com:8443`)
- Supported Instances (comma-separated: `Development,Test,Production`)
- Proxy Port (default: 3000)
- Service Port (default: 1337)

When an entry changes a default or adds a field, state the default value explicitly:

```
- **Settings**: Added Service Port field (default: 1337) for device service communication.
  Existing installations default to 1337 with no action required.
```

### Tool Window First-Run (`isApplicable` gating)

Tool windows only appear in Keyscript projects. When writing about this behavior:

```
- **Tool Windows**: Keyscript Workspace, Data Tools, and Diagnostics now use
  `secondary="true"` in the tool window descriptor, preventing them from
  auto-activating on IDE startup in non-Keyscript projects. They appear only
  when a Keyscript project is open.
```

### Login Dialog

The login dialog collects: Keystone server URL, instance name, device ID, username, password.
When describing login flow changes, name which field or behavior changed:

```
- **Login Dialog**: The dialog now closes automatically on successful authentication.
  Previously it remained open and required a manual dismiss.
```

---

## Tone for Onboarding Entries

- Imperative or present-tense ("opens", "detects", "appears") — not past tense ("was added").
- Address the user's goal, not the system's action: "so you can authenticate without navigating
  to Settings" rather than "the login button was moved."
- Avoid implementation jargon (PasswordSafe, JSESSIONID) unless the entry is in a
  technical/under-the-hood section.
