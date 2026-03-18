7 files written to `.Codex/skills/crafting-empty-states/`:

```
SKILL.md
references/
  activation-onboarding.md
  engagement-adoption.md
  in-app-guidance.md
  product-analytics.md
  roadmap-experiments.md
  feedback-insights.md
```

Key things grounded in the actual codebase:

- **Four state taxonomy** mapped to the four distinct patterns already in the code: auth-gated (`SessionPanel`), loading (`TableBrowserPanel`), no-selection (`NetworkPanel` CardLayout), unconfigured
- **Coverage gap matrix** in `roadmap-experiments.md` — shows exactly which panels are missing which states as of v2.0.0, with a prioritized sprint plan
- **`UIUtil.getContextHelpForeground()` rule** documented with the existing `JBColor.GRAY` inconsistency in NetworkPanel called out
- **Silent catch block detection** grep command in `feedback-insights.md` to find existing places where errors are swallowed without user feedback
- **WARNING: No analytics layer** in `product-analytics.md` — honest about the gap, with a `[keyscript.*]` log prefix convention as a pragmatic stopgap