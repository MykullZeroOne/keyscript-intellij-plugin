# Strategy & Monetization Reference

## Contents
- Current Tier Model
- Value Ladder Design
- Feature Allocation by Tier
- Upgrade Logic Implementation
- Anti-Patterns

---

## Current Tier Model

Keyscript IDE has two implicit tier axes:

**IDE Edition Axis** (not controlled by us):
```
Community Edition  →  Ultimate Edition
  (free, limited)      (paid, full features)
```

**Keystone Instance Axis** (controlled by org admins):
```
Development  →  Test  →  Production
  (explore)    (validate)  (deploy)
```

The plugin today doesn't enforce the instance ladder — any configured user can switch to Production. If instance access should be gated (e.g., Production requires approval), that logic belongs in `SessionService` or `AuthenticationService`.

---

## Value Ladder Design

The value ladder should feel like a natural progression, not a paywall. Map each tier to a concrete job-to-be-done:

| Tier | Job to Be Done | Key Features | Gate |
|------|---------------|--------------|------|
| Community + Dev | "I want to write and test Keyscript" | Completions, run scripts, Data Tools | None |
| Ultimate + Dev | "I want to see results instantly" | JCEF live preview, JS language tooling | IntelliJ Ultimate |
| Any + Production | "I want to deploy with confidence" | Production deployments, real data | Instance access |

Design principle: **each tier unlocks a new job, not just more features.** "More features" is weak; "new capability" is compelling.

---

## Feature Allocation by Tier

Current allocation (inferred from codebase):

```
COMMUNITY + ANY INSTANCE
  ✅ CR framework completions (CR.XML, CR.Core, CR.Login, CR.Script, Ext.*)
  ✅ Live templates
  ✅ Run gutter icon + Ctrl+Shift+F10
  ✅ Workspace tool window (Run Options, Session)
  ✅ Data Tools (Search, Table Browser, Query Builder)
  ✅ Diagnostics (Console, Network Monitor)
  ✅ New Project wizard (all templates)
  ✅ keyscript.bundle.json management

ULTIMATE + ANY INSTANCE
  ✅ Everything above
  ✅ JCEF split-editor browser preview
  ✅ JavaScript language services (type checking, go-to-definition)

ANY EDITION + PRODUCTION INSTANCE
  ✅ Deploy scripts to Production Keystone
  ✅ Production table access via Data Tools
```

If a new feature is being added, decide its tier before implementation, not after. Retrofitting gates is painful.

---

## Upgrade Logic Implementation

### Tier Decision Tree

```
User tries to open split editor
    └─ isUltimateEdition()?
        ├─ YES → open JCEFBrowserPanel
        └─ NO  → show TierUpgradePanel("Live preview requires Ultimate")

User selects Production instance
    └─ isProductionAccessAllowed()?  ← implement this if access control is needed
        ├─ YES → allow session switch
        └─ NO  → notify("Production access requires org approval")
```

### Gating a New Feature

```kotlin
// Pattern for any future tier-gated feature
fun buildMyFeaturePanel(project: Project): JComponent {
    return when {
        !isUltimateEdition() -> TierUpgradePanel(
            feature = "My Feature Name",
            reason = "Requires IntelliJ IDEA Ultimate",
            outcome = "Description of what the user gets"
        )
        !hasProductionAccess() -> TierUpgradePanel(
            feature = "My Feature Name",
            reason = "Requires Production instance access",
            outcome = "Contact your Keystone admin to enable Production access"
        )
        else -> MyFeaturePanel(project)
    }
}
```

---

## Anti-Patterns

### WARNING: Undocumented Tier Walls

**The Problem:** Adding a gate in code without updating README.md or plugin.xml.

**Why This Breaks:** Users hit the wall, see no explanation in docs, and file bugs. Trust erodes. Marketplace reviews go negative.

**The Fix:** Every gate in code must have a corresponding entry in:
1. README.md feature table (`Community` vs `Ultimate` column)
2. plugin.xml description (if it's a major feature)
3. CHANGELOG.md for the version that introduced the gate

### WARNING: Monetizing Before Value Is Proven

NEVER move a feature from Community to Ultimate-only after users have been using it for free. This is a hostile downgrade. Gate features at introduction, not after adoption.

**The exception:** If the feature is impractical in Community for technical reasons (e.g., requires a JavaScript language server that only ships with Ultimate), that's a natural gate — not a monetization decision.

---

See the **clarifying-market-fit** skill for ICP context that should drive tier allocation decisions.
See the **orchestrating-feature-adoption** skill for rollout strategy when introducing a new tier.
See the **scoping-feature-work** skill for the decision framework when placing a new feature in the tier model.
