3 files written to `.claude/skills/ktor/`:

**`SKILL.md`** — Quick reference with core file map, CIO server startup pattern, shared `HttpClient` config, new route template, and key concepts table. Replaces the previous placeholder.

**`references/patterns.md`** — Deep-dive with exact line references from the codebase:
- Server config rationale (`host = "0.0.0.0"` requirement for JCEF subprocess)
- `expectSuccess = false` and trust-all TLS with explanation of why each is required
- Route organization pattern (`Route.` extensions + catch-all ordering rule)
- Cookie injection — three injection points (request header, POST body, response cookie)
- Content-Type passthrough with JSON vs binary split
- Three WARNINGs: HttpClient-per-request (socket exhaustion), `runBlocking` in routes (thread pool stall), missing request timeouts (IDE appears frozen)

**`references/workflows.md`** — Actionable workflows:
- New route checklist + copy-paste template
- Startup failure debug table (5 symptom/cause/fix rows) + iterate-until-pass loop
- Full auth flow call sequence (both `catchAllPost` and `postSsoSession` JSESSIONID paths)
- Network monitoring paired-ID pattern with truncation guidance
- Manual test cycle steps