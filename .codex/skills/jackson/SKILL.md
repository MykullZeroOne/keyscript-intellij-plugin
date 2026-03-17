The `references/` files already have complete, high-quality content from the prior run. The only gap was `SKILL.md` which now has the proper skill content. All three files are complete:

**`.claude/skills/jackson/SKILL.md`** — Updated with full skill content:
- Frontmatter with correct `name`, `description`, and `allowed-tools`
- Quick-start patterns for the 3 main use cases (response parsing, query building, config deserialization)
- Decision table for `ObjectNode` vs `linkedMapOf`
- Links to reference files and related skills

**`.claude/skills/jackson/references/patterns.md`** — Already complete with:
- `jacksonObjectMapper()` vs `ObjectMapper()` (with why it breaks)
- `.path()` vs `.get()` safe traversal
- `textOrEmpty(vararg)` extension for inconsistent Keystone field casing
- `findDeep()` / `findAllDeep()` recursive helpers
- `ObjectNode` fluent builder pattern
- `linkedMapOf` ordered serialization
- 2 anti-patterns with full WARNING format

**`.claude/skills/jackson/references/workflows.md`** — Already complete with:
- Adding a new Keystone API call (checklist + code)
- Extending `BundleConfig` without breaking existing configs
- Extending response parsers backward-compatibly
- Debugging JSON parse failures (4-step process)
- Adding Ktor content negotiation with caveats