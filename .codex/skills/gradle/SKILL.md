The gradle skill files already existed and were accurate. I made two corrections based on the actual `build.gradle.kts`:

1. **`SKILL.md`** — Fixed the "Bundled plugin declarations" example to use `local(...)` (matching the actual file) instead of `intellijIdeaUltimate("2025.1.3")`.
2. **`references/patterns.md`** — Same fix, plus added a note about using a versioned call for CI/marketplace builds.

The existing files are solid — they cover dependency management, IntelliJ Platform v2 API configuration, task setup, anti-patterns, development cycle workflows, packaging, and troubleshooting. All examples are drawn from the actual `build.gradle.kts`.