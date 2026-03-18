# Strategy & Monetization Reference

## Contents
- Plugin Monetization Model Options
- Free vs. Paid Feature Gating
- Pricing Surface in the Plugin
- Upgrade Path Engineering
- Anti-Patterns

---

## Plugin Monetization Model Options

The Keyscript IDE plugin currently has no monetization layer. Three options fit the architecture:

| Model | Mechanism | Fit for Keyscript |
|-------|-----------|-------------------|
| **Free** | No gating, all features open | Current state; maximizes adoption in Keystone shops |
| **Freemium** | Community (free) vs. Ultimate (paid) feature gating | Best fit — maps to IntelliJ Community/Ultimate split |
| **License key** | Keystone instance license gates advanced features | Works if Keystone licensing already exists |

The most natural model given the existing IntelliJ Community/Ultimate target is **freemium with edition-based gating**.

---

## Free vs. Paid Feature Gating

The plugin already targets IntelliJ IDEA Ultimate for full features (JCEF preview requires JBR, Community has reduced tooling). This creates a natural tier:

| Feature | Community | Ultimate |
|---------|-----------|---------|
| CR framework completions | ✓ | ✓ |
| Run gutter icon | ✓ | ✓ |
| Settings + project detection | ✓ | ✓ |
| JCEF split-editor preview | Limited | ✓ |
| Network Monitor | ✓ | ✓ |
| Table Browser | ✓ | ✓ |
| Query Builder | ✓ | ✓ |
| Deploy action | ✓ | ✓ |

The JCEF preview is already gated by IDE edition (JCEF is part of JBR in Ultimate). No plugin code change is needed to have a natural tier boundary.

### Implementing a paid feature gate

If adding a custom paid tier (e.g., "Keyscript Ultimate license"):

```kotlin
// LicenseService — check for valid license before rendering premium UI
@Service(Service.Level.APP)
class LicenseService {
    private var _isLicensed: Boolean = false

    fun checkLicense(key: String): Boolean {
        // Validate key against Keystone licensing endpoint
        _isLicensed = validateWithServer(key)
        return _isLicensed
    }

    val isLicensed: Boolean get() = _isLicensed
}

// In a premium panel factory — gate the UI
class QueryBuilderPanelFactory {
    fun createPanel(project: Project): JComponent {
        if (!LicenseService.getInstance().isLicensed) {
            return createUpgradePromptPanel("Query Builder requires Keyscript Ultimate")
        }
        return QueryBuilderPanel(project)
    }
}
```

See the **structuring-offer-ladders** skill for full tier design and upgrade copy patterns.

---

## Pricing Surface in the Plugin

The plugin's pricing surfaces — where users encounter upgrade prompts — are:

1. **Locked feature UI** — gray-out + "Upgrade to Keyscript Ultimate" label
2. **Empty states** — "Sign in with Keyscript Ultimate to enable Query Builder"
3. **Settings panel** — "License" section for key input
4. **Notification on feature attempt** — "This feature requires Keyscript Ultimate"

### Upgrade prompt pattern

```kotlin
// In any gated panel — consistent upgrade prompt UI
private fun createUpgradePanel(featureName: String): JPanel {
    return panel {
        row {
            icon(AllIcons.General.Lock)
            label("<html><b>$featureName</b> is a Keyscript Ultimate feature.</html>")
        }
        row {
            link("Learn about Keyscript Ultimate →") {
                BrowserUtil.browse("https://keyscript.example.com/upgrade")
            }
        }
    }
}
```

**Key principle:** NEVER hide gated features. Show them grayed out with an explanation. Hidden features don't create upgrade desire — visible-but-locked features do.

---

## Upgrade Path Engineering

### Trial experience

The most effective upgrade path for a developer tool: let users experience the feature fully for a trial period before gating.

```kotlin
// TrialService — track trial usage
@Service(Service.Level.APP)
@State(name = "KeyscryptTrial", storages = [Storage("keyscrypt-trial.xml")])
class TrialService : PersistentStateComponent<TrialService.State> {
    data class State(
        val trialStartedAt: Long = 0,
        val trialRunsUsed: Int = 0
    )

    private var _state = State()
    override fun getState() = _state
    override fun loadState(state: State) { _state = state }

    val trialActive: Boolean
        get() = _state.trialStartedAt > 0 &&
                System.currentTimeMillis() - _state.trialStartedAt < 14.days.inWholeMilliseconds

    fun consumeTrial(): Boolean {
        if (!trialActive) return false
        _state = _state.copy(trialRunsUsed = _state.trialRunsUsed + 1)
        return true
    }
}
```

14-day trial gives enough time for the developer to integrate the tool into their workflow — the strongest predictor of upgrade.

---

## Anti-Patterns

### WARNING: Gating the wrong features

NEVER gate features that create adoption friction for the team:
- Auto-detection (gates team spread)
- Basic run/execute (gates core value delivery)
- Settings UI (gates any usage at all)

Gate features that deliver *professional* value after baseline adoption:
- Advanced query generation
- Deploy with diff preview
- Team license sharing

### WARNING: Surprise paywalls after free trial

```kotlin
// BAD — silent trial expiry that just breaks features
fun canUseQueryBuilder(): Boolean = trialActive || isLicensed

// GOOD — proactive notice before trial expires, clear path after
fun canUseQueryBuilder(): Boolean {
    if (!isLicensed) {
        val daysLeft = trialDaysRemaining()
        if (daysLeft <= 3) notifyTrialExpiringSoon(daysLeft)
        if (daysLeft <= 0) {
            showUpgradePrompt("Query Builder")
            return false
        }
    }
    return true
}
```

Surprise blocks destroy trust. Developers who feel ambushed leave 1-star reviews and uninstall.

See the **structuring-offer-ladders** skill for the full tier ladder design including Keystone instance tiers (Development/Test/Production) as a monetization angle.
