The skill files are now complete. Here's what was updated:

**`SKILL.md`** — Replaced the generator output message with a proper skill file containing:
- Frontmatter with `name`, `description`, `allowed-tools`
- Quick-start code blocks for the three most common patterns (project service, app service, action with guard)
- Key concepts table
- Two common UI patterns with real code examples
- Cross-references to related skills (kotlin, gradle, jcef, ktor)

**`references/patterns.md`** — Already had solid content:
- Service declaration and retrieval
- Listener/observer pattern from `SessionService`
- Thread safety rules table
- Settings persistence (`PersistentStateComponent`) vs secrets (`PasswordSafe`)
- `plugin.xml` registration examples
- Three anti-patterns with problem/consequence/fix format

**`references/workflows.md`** — Already had solid content:
- Build feedback loop with `mcp__jetbrains__build_project`
- Checklists for adding service, action, tool window, and settings configurable
- Build error validation guide