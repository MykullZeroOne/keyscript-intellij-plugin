Here are the generated files:

---

**`.claude/skills/intellij-platform/SKILL.md`** — Quick reference covering service registration, project gating, settings persistence, notification pattern, EDT safety, and cross-links to related skills (kotlin, ktor, gradle, jcef).

**`references/patterns.md`** — Deep patterns reference with real code from this codebase:
- Service declaration (`@Service` + `getInstance` companion)
- Listener pattern using `CopyOnWriteArrayList` (from `SessionService`)
- Thread safety rules table (EDT vs BGT)
- `PersistentStateComponent` for settings vs `PasswordSafe` for secrets
- `plugin.xml` registration patterns
- Three documented anti-patterns: blocking EDT, missing `getActionUpdateThread()`, accessing disposed services

**`references/workflows.md`** — Step-by-step checklists for the four most common extension tasks:
- Build & run with feedback loop
- Add a new service (6-step checklist)
- Add a new action (5-step checklist)
- Add a new tool window (5-step checklist)
- Add a settings configurable
- Build error validation guide