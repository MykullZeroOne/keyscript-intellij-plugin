Generated 3 files at `.claude/skills/ktor/`:

**`SKILL.md`** — Quick reference covering:
- When to use this skill (proxy routes, cookie injection, HttpClient config, network monitoring)
- Server startup pattern with CIO engine
- Adding new proxy routes (template)
- HttpClient configuration with trust-all TLS and `expectSuccess = false`
- Key concepts table mapping concepts to files

**`references/patterns.md`** — Detailed patterns covering:
- Server/client configuration with rationale
- Route organization (`Route.` extensions called from `configure()`)
- Cookie injection — why you NEVER replace the full header
- Content-Type passthrough — why hardcoding JSON breaks XML endpoints
- Two WARNINGs: HttpClient-per-request (socket exhaustion) and `runBlocking` in Ktor routes (deadlock)

**`references/workflows.md`** — Step-by-step workflows covering:
- Adding a new proxy route (copyable checklist + template)
- Debugging proxy startup failures (symptom/cause/fix table + iterate-until-pass loop)
- Full authentication flow walkthrough (call sequence diagram)
- Network monitoring — paired request/response ID pattern
- Build and manual test cycle