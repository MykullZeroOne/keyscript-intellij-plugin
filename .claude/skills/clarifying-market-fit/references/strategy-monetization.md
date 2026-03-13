# Strategy and Monetization Reference

## Contents
- Current positioning: internal vs public
- Community Edition vs Ultimate Edition differentiation
- Feature tiering in plugin.xml
- Messaging for tier boundaries

---

## Current Positioning

Keyscript IDE is positioned as a **developer productivity tool for Keystone teams**, not a commercial product with public pricing. The primary "monetization" is organizational adoption: teams standardizing on IntelliJ + this plugin instead of the standalone Electron IDE.

The strategic goal is full parity with the Electron IDE, not expansion beyond the Keystone workflow. Resist scope creep in both features and messaging — adding "generic JS IDE" capabilities dilutes the value proposition for the core ICP.

---

## Community Edition vs Ultimate Edition Differentiation

The plugin targets **IntelliJ IDEA Ultimate** but runs on Community Edition with reduced functionality. This is already documented in the README. The messaging opportunity is to frame this clearly so Community users understand what they're missing — and Ultimate users see clear justification:

```markdown
<!-- README.md — feature availability table -->
## IntelliJ Edition Compatibility

| Feature | Community | Ultimate |
|---------|-----------|---------|
| Script run & deploy | Yes | Yes |
| JCEF live preview | No (no JCEF support) | Yes |
| CR framework completions | Partial | Full |
| Data Tools panel | Yes | Yes |
| Diagnostics panel | Yes | Yes |

**Recommended:** IntelliJ IDEA Ultimate for the full JCEF preview experience.
```

### WARNING: Don't Hide Edition Requirements

**The Problem:**

```xml
<!-- BAD — plugin.xml with no edition callout -->
<description><![CDATA[
  <p>Full-featured Keyscript IDE with live preview, code completions, and data tools.</p>
]]></description>
```

**Why This Breaks:**
1. Community Edition users install expecting "live preview", find it missing, leave a 1-star review
2. Mismatched expectations damage the plugin's Marketplace reputation
3. Community users who need JCEF will be stranded with no guidance

**The Fix:**

```xml
<!-- GOOD — sets correct expectations upfront -->
<description><![CDATA[
  <p>Keyscript IDE for Keystone script development.</p>
  <p><b>Live JCEF preview requires IntelliJ IDEA Ultimate.</b>
  All other features work in Community Edition.</p>
]]></description>
```

---

## Feature Tiering in Plugin Code

Gating features by edition at the code level:

```kotlin
// Check for Ultimate features before enabling JCEF preview
import com.intellij.openapi.application.ApplicationInfo

fun isUltimateEdition(): Boolean {
    return ApplicationInfo.getInstance().fullApplicationName.contains("Ultimate")
}

// In KeyscriptSplitEditorProvider.kt
override fun accept(file: VirtualFile): Boolean {
    return file.isKeyscriptFile() && isUltimateEdition()
}
```

Pair code gating with clear copy when a user hits a tier boundary:

```kotlin
// WorkspacePanel.kt — preview tab unavailable on Community
if (!isUltimateEdition()) {
    previewTab.isEnabled = false
    previewTab.toolTipText = "Live preview requires IntelliJ IDEA Ultimate."
}
```

---

## Keystone Instance Tiers as a Value Ladder

The plugin's `supportedInstances` setting (e.g., `Development,Test,Production`) maps directly to the Keystone deployment tier structure. This is a natural value ladder: developers start in Development, promote to Test, and deploy to Production. Messaging can reinforce this workflow progression:

```kotlin
// Settings hint for instance configuration
row("Supported Instances:") {
    textField()
        .bindText(settings::supportedInstances)
        .comment(
            "Enter Keystone instance names in promotion order. " +
            "Example: Development,Test,Production"
        )
}
```

This framing positions the plugin as part of the deployment workflow, not just a script editor — which increases perceived value and justifies requiring team adoption.

See the **structuring-offer-ladders** skill for detailed tier messaging and upgrade prompt patterns.
