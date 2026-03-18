5 files written to `.Codex/skills/typescript/`:

```
SKILL.md
references/
  patterns.md
  types.md
  modules.md
  errors.md
```

Key decisions made based on the actual codebase:

- **Reframed the skill** — "TypeScript" here primarily means `cr-framework.d.ts` (ambient declarations for completions) and plain `.keyscript.js` user scripts, not a TS compilation pipeline
- **No tsconfig/package.json** in the plugin repo — documented the vendored bundle architecture clearly
- **WARNING: Don't edit keyscript-all.js** — 31K lines of minified JS, replace-only
- **WARNING: `"format": "esm"` in bundle config** — silently fails in plain `<script>` JCEF context
- **WARNING: Missing errorCode check** — Keystone returns HTTP 200 for business logic failures
- Cross-references to **jcef**, **ktor**, **kotlin**, **gradle**, and **intellij-platform** skills throughout