# Strategy and Monetization Reference

## Contents
- Positioning for the JetBrains Marketplace
- Free vs. paid strategy options
- Pricing and offer messaging
- Competitive differentiation copy
- Anti-patterns

## Positioning for the JetBrains Marketplace

This plugin occupies a specific, narrow position: **the only IDE for Keyscript development inside IntelliJ**. That's a category-of-one position — lean into it.

Positioning statement (internal reference):
> For Keystone developers who use IntelliJ IDEA, Keyscript IDE is the only plugin that replicates the full Electron IDE experience — proxy authentication, JCEF preview, CR completions, and deploy actions — inside their existing IDE.

This belongs in:
- Marketplace listing subheadline
- README hero
- Any comparison or competitive context

NEVER soften this with hedging like "one of the best" or "a great option." Own the category.

See the **clarifying-market-fit** skill for full ICP and positioning work.

## Free vs. Paid Strategy

The plugin is currently free. If a paid tier is introduced, the messaging strategy must shift:

### Free tier framing
```markdown
<!-- Free tier — lower activation bar -->
## Free to install. No account needed beyond your Keystone credentials.
```

### Paid tier framing (if introduced)
```markdown
<!-- Hypothetical paid tier — value-first, not feature-list -->
## Keyscript IDE Pro

For teams running multiple Keystone environments.
Includes team-shared configurations, advanced query builder,
and priority support.

[Compare plans →]
```

For an IntelliJ plugin targeting a B2B developer audience, pricing by seat (per developer) is conventional. See the **structuring-offer-ladders** skill if a multi-tier model is being designed.

## Offer and Upgrade Messaging

If a freemium model is introduced, upgrade prompts should:
1. Appear in context of the gated feature (not randomly)
2. State the specific unlock, not the tier name
3. Have one action: upgrade

```kotlin
// Example: Pro feature gate in QueryBuilderPanel
val proPrompt = JPanel().apply {
    add(JLabel("Advanced filters are available in Keyscript IDE Pro."))
    add(ActionLink("Upgrade →") {
        BrowserUtil.browse("https://plugins.jetbrains.com/plugin/[id]/pricing")
    })
}
```

NEVER use dark patterns: no countdown timers, no "Your trial expires soon" for features they haven't used.

## Competitive Differentiation Copy

If users come from the Electron Keyscript IDE, acknowledge the migration:

```markdown
## Migrating from the Electron IDE?

Drop-in replacement. Same proxy auth, same session handling, same CR completions —
just inside IntelliJ. Point the plugin at your existing Keystone server and you're running.
```

If the Electron IDE is sunset, make the urgency explicit:
```markdown
## The Electron IDE is being retired

Keyscript IDE for IntelliJ is the supported path forward.
All Electron IDE features are available in the plugin today.
```

## Pricing Page Copy Principles

If a pricing page is added to a companion marketing site:

| Section | Copy principle |
|---------|---------------|
| Plan names | Use capability descriptors (Starter, Teams) not tier numbers |
| Feature lists | Lead with outcomes, not API names |
| CTA | "Get [Plan Name]" not "Buy Now" — developers are sensitive to transaction language |
| FAQ | Pre-empt "Can I use it on my personal machine?" and "What happens if I cancel?" |

## Anti-Patterns

### WARNING: Positioning as "general purpose" to broaden appeal

**The Problem:** Softening "for Keystone developers" to "for JavaScript developers" to reach a wider audience.

**Why This Breaks:** The plugin's features (proxy auth, JSESSIONID injection, CR completions) are meaningless to non-Keystone developers. Broad positioning increases installs from the wrong audience, driving negative reviews and low activation rates.

**The Fix:** Stay specific. "For Keystone developers using IntelliJ" is a feature, not a limitation — it signals to the right audience that this tool was built for them.

### WARNING: ROI copy for a free tool

Phrases like "Save hours every week" or "10x your productivity" require proof you don't have.

Use specific, concrete claims instead:
- **BAD:** "Save hours switching between IDEs"
- **GOOD:** "Run scripts without leaving IntelliJ — the browser preview is in the IDE"
