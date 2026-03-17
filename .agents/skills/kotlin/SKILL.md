All five files written. Here's what was created:

**`.Codex/skills/kotlin/`**
- `SKILL.md` — Quick-start with service declaration pattern, async pattern, key concepts table, and links to references
- `references/patterns.md` — Service lifecycle checklist, listener pattern with `CopyOnWriteArrayList`, EDT threading, null safety, and two anti-patterns with real consequences
- `references/types.md` — `ApiResult`/`LoginResult` data classes, sealed classes for state, settings persistence with `PersistentStateComponent`, Java interop null safety, Jackson typing anti-pattern
- `references/modules.md` — Package structure, cross-module service access, checklists for adding services/actions/tool windows, `plugin.xml` registration table
- `references/errors.md` — Logger usage, notification pattern, session expiry flow, `runCatching`, and three anti-patterns (swallowing exceptions, throwing across thread boundaries, UI on wrong thread)

All examples are grounded in the actual codebase patterns (`SessionService`, `DeploymentService`, `KeystoneApiClient`, etc.).