# Product Analytics — Release Note Writing Guide

This reference covers how to describe performance improvements, architectural gains, and
measurable behavior changes in release notes for Keyscript IDE, where traditional product
analytics (event tracking, funnels) are replaced by observable technical metrics.

---

## What "Analytics" Means for an IDE Plugin

Keyscript IDE does not have a telemetry pipeline. "Analytics" in release notes means:
- **Measurable performance gains** — startup time, proxy latency, build time.
- **Reduced friction counts** — fewer steps, fewer clicks, fewer config fields.
- **Architectural changes with visible effects** — lazy init, project gating, secondary tool windows.
- **Resource reduction** — memory footprint, thread usage, dependency size.

When writing release notes, translate technical metrics into user-observable outcomes.

---

## Performance Framing Patterns

### Pattern 1 — Startup time improvement

Always name the specific startup phase and the mechanism:

```
- **Proxy Startup**: The embedded Ktor server now starts lazily on the first script run
  rather than at IDE startup, eliminating 3–5 seconds of initialization overhead for
  projects that are opened but not yet active.
```

Do not write: "Improved proxy startup performance." — this is not measurable or verifiable
by the reader.

### Pattern 2 — Engine or dependency change

Name the old and new component and state the user-visible effect:

```
- **Proxy Engine**: Switched from Netty to the Ktor CIO engine. CIO has no native
  library dependency and starts in under 200ms vs Netty's 3–5 second JNI loading.
  No configuration change is required.
```

### Pattern 3 — Project-scoped activation (zero overhead)

Frame this as an explicit guarantee, not a vague "performance improvement":

```
- **Project Gating**: Plugin services (proxy, session, completions) no longer activate
  in non-Keyscript projects. Opening a Java or Python project in the same IDE instance
  incurs zero overhead from the Keyscript plugin.
```

### Pattern 4 — Build time reduction

Gradle build changes that affect developer iteration speed:

```
- **Build**: Disabled `buildSearchableOptions` task. This task indexed all settings
  for IntelliJ's search, adding ~30 seconds to every plugin build. The task is not
  required for development builds and can be re-enabled before Marketplace submission.
```

---

## Keyscript-Specific Metrics to Track and Report

### Proxy Server (`ProxyServerService`, `KtorProxyServer`)

| Metric | How to describe it |
|--------|--------------------|
| Startup latency | "Starts in under X ms" or "eliminated Xs startup overhead" |
| Engine | "CIO engine (no Netty, no JNI)" |
| Init trigger | "Starts on first run/login/search, not at project open" |
| Port | "Default port 3000; configurable in Settings > Keyscript IDE" |

### Session Management (`SessionService`)

| Metric | How to describe it |
|--------|--------------------|
| Heartbeat interval | "Checks session validity every N seconds in the background" |
| Auto-relogin | "Re-authenticates automatically using saved PasswordSafe credentials" |
| EDT blocking | "Login and heartbeat run off the EDT; no UI freeze during auth" |

### Project Detection (`KeyscriptProjectDetector`)

| Metric | How to describe it |
|--------|--------------------|
| Detection scope | "Checks for keyscript.bundle.json, .keyscript, *.keyscript.js, or @keyscript comment" |
| Overhead in non-Keyscript projects | "Zero — detection runs once at project open, not continuously" |

### Tool Window Activation

Tool windows use `secondary="true"` which prevents auto-activation:

```
- **Tool Windows**: All three Keyscript panels (Workspace, Data Tools, Diagnostics) use
  `secondary="true"` in the tool window descriptor. They no longer restore their open
  state on IDE restart, preventing accidental activation in non-Keyscript sessions.
```

---

## Describing Architecture Changes with User Impact

Some v2.0.0 changes are architectural but have strong user-visible effects. Use this pattern:

```
[What changed architecturally] → [What the user observes differently]
```

Examples:

```
CIO engine replaces Netty → Proxy starts instantly on first run; no multi-second delay.

Lazy proxy initialization → IDE opens in the same time whether or not a Keyscript
  project is loaded.

PasswordSafe credential storage → Credentials survive IDE restart; no re-entry
  required on next session.

Listener pattern in SessionService → Status bar and Workspace panel update immediately
  when session state changes, without polling.
```

---

## Numbers to Use and Numbers to Avoid

Use:
- Specific ranges: "3–5 seconds", "under 200ms", "port 3000"
- Counts: "14 project services", "9 step subelements", "5 CR namespaces"
- Build ranges: "builds 251–253.*"

Avoid:
- Vague percentages: "50% faster" without a baseline
- "Significantly improved" without a qualifier
- Internal class counts or line counts (not meaningful to users)
