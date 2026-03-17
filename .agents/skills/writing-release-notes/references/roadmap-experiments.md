# Roadmap & Experiments — Release Note Writing Guide

This reference covers how to write release notes for experimental features, preview capabilities,
and planned work in Keyscript IDE. It also covers how to describe IDE edition differences
(Community vs Ultimate) and platform build compatibility.

---

## Experimental and Beta Features

Keyscript IDE does not use a formal feature flags system. "Experimental" in this context means:
- A feature is implemented but not fully hardened (e.g., no error recovery, limited test coverage).
- A feature depends on a third-party capability that may change (e.g., JCEF API surface).
- A feature is behind a settings toggle that is off by default.

### Writing Pattern — Experimental flag

Add a `[Experimental]` prefix to the entry heading. Describe what is and is not supported yet.

```
- **[Experimental] SSO/Kerberos Login**: Single Sign-On via GET /UserLogin is now
  supported for environments configured with Kerberos authentication. Full Kerberos
  ticket negotiation is not yet implemented; the feature works for SSO environments
  that accept a direct GET with session parameters.
```

### Writing Pattern — Preview capability

Use "Preview" for features that are functional but may change API or behavior:

```
- **[Preview] esbuild Bundle Action**: The Keyscript > Bundle menu item runs esbuild
  on the current React/Node project. Output goes to the `dist/` directory. The action
  requires `esbuild` to be available on PATH. Configuration options (entry point,
  target, minify) are not yet exposed in the UI — they use project defaults.
```

---

## Community Edition vs Ultimate

### Surfaces affected by edition

| Feature | Ultimate | Community |
|---------|----------|-----------|
| JCEF split-editor preview | Full support | Not available (no JCEF in Community) |
| JavaScript bundled plugin | Available | Not available without manual install |
| CR framework completions | Full | Full (language-independent) |
| Tool windows | Full | Full |
| Run configurations | Full | Full |
| Table Browser / Query Builder | Full | Full |
| Status bar widget | Full | Full |

### Writing Pattern — CE limitation

When a feature requires Ultimate, state this explicitly and describe the fallback:

```
- **Split-Editor Preview**: The JCEF browser preview panel requires IntelliJ IDEA
  Ultimate or a JetBrains IDE with JCEF support. In Community Edition, the preview
  tab does not appear; scripts still run and output appears in the Diagnostics console.
```

### Writing Pattern — CE note in a multi-feature entry

If a release note covers a feature that works differently across editions, add a note at
the end of the entry rather than a separate entry:

```
- **CR Framework Completions**: Added Ext.* and CR.* namespace completions for all
  Keyscript JavaScript files.
  Note: In Community Edition, completions require the JavaScript plugin to be installed
  separately (Settings > Plugins > JavaScript).
```

---

## Build Range Compatibility

The plugin targets IntelliJ builds 251–253.* (IDEA 2025.1 through 2025.3).

### Writing Pattern — Compatibility note for a specific build

```
- **Platform**: This release adds compatibility with IntelliJ IDEA 2025.3 (build 253.*).
  The `sinceBuild = "251"` and `untilBuild = "253.*"` range is unchanged; both existing
  and new installations are covered.
```

### Writing Pattern — Breaking platform change

When the `sinceBuild` or `untilBuild` changes, announce it prominently as a Breaking Change:

```
### Breaking Changes
- **Platform Compatibility**: Minimum supported build raised from 251 to 252 (IDEA 2025.2).
  IntelliJ IDEA 2025.1 is no longer supported. Update your IDE before installing this version.
```

---

## Roadmap Items in Release Notes

Roadmap mentions belong in release notes only when:
1. A partial implementation ships (e.g., test infrastructure without test cases).
2. A known gap is being acknowledged to set expectations.
3. A deprecated feature is being sunset with a future removal date.

### Writing Pattern — Partial implementation

```
- **Test Infrastructure**: The project now includes IntelliJ plugin test fixtures
  (`MockProject`, `MockPsiElement`) in the test source set. No test cases are
  included in this release; the infrastructure supports future test authoring.
```

### Writing Pattern — Known gap acknowledgment

```
- **Query Builder**: XML export does not yet support multi-level nested sequences.
  Queries with more than one Sequence node under a Transaction will flatten to a
  single level in the XML Preview tab. Full nesting support is planned for v2.1.
```

### Writing Pattern — Deprecation notice

```
- **Deprecated**: The `keystoneServer` setting key used in v1.x configuration files
  is deprecated and will be removed in v2.1. Rename it to `keystoneServer` in
  `keyscript.bundle.json`. A migration warning appears in the Diagnostics console
  when the old key is detected.
```

---

## Tone for Roadmap Entries

- Be specific about what is and is not included. "Coming soon" is not useful in a changelog.
- Use "planned for vX.Y" or "not yet supported" rather than "will be added."
- If a feature is gated to a settings toggle, name the exact toggle path.
- Avoid roadmap promises in patch releases; reserve them for minor and major version notes.
