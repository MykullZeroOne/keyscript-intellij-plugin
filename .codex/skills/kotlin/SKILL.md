The `kotlin` skill is now complete. Here's what's in each file:

**`SKILL.md`** — Rewritten with actual codebase patterns: the service declaration pattern, thread-safe listener pattern (from `SessionService`), background task pattern (from `KeyscryptProjectService`), atomic state guards, and double-checked lazy init (from `ProxyServerService`).

**`references/patterns.md`** — Service lifecycle checklist, `CopyOnWriteArrayList` listener pattern, EDT threading rules, null safety idioms, and two WARNING anti-patterns (calling services in `dispose()`, using `lateinit var` instead of constructor injection).

**`references/types.md`** — `ApiResult`/`LoginResult` data class patterns with `sessionExpired` flag, sealed classes for `LoginState` vs Boolean flags, enum for API constants, `PersistentStateComponent` for settings, and Java interop null safety.

**`references/modules.md`** — Package structure diagram, cross-module service access via `project.service<T>()`, and copyable checklists for adding a new service, action, or tool window with the exact `plugin.xml` registration required.

**`references/errors.md`** — Logger levels and what to log at each, notification patterns, session expiry flow, `runCatching` recovery, and three WARNINGs: swallowing exceptions silently, uncaught exceptions in pooled threads, and UI operations off the EDT.