# Distribution Reference

## Contents
- Distribution channels for this plugin
- Channel-specific copy requirements
- JetBrains Marketplace listing anatomy
- GitHub README as distribution surface
- Update and re-engagement copy

## Distribution Channels

| Channel | Copy format | Character limits | Update cadence |
|---------|-------------|-----------------|----------------|
| JetBrains Marketplace | HTML + Markdown | Title: 60, Description: 3000 | Per release |
| GitHub README | Markdown | None | Per release or on-demand |
| CHANGELOG.md | Markdown | None | Per release |
| In-plugin "What's New" | Plain text or HTML | ~500 | Per major release |

## JetBrains Marketplace Listing Anatomy

The Marketplace listing renders a full description page. Structure it as:

```markdown
## [Plugin Name]

[One-sentence value prop targeting Keystone developers]

---

### What it does

[3-4 bullets: user outcomes, not feature names]

### Key features

- **[Feature name]**: [Outcome sentence]
- **[Feature name]**: [Outcome sentence]
- **[Feature name]**: [Outcome sentence]

### Requirements

- IntelliJ IDEA 2025.1+ (Ultimate recommended)
- Access to a Keystone server instance

### Getting started

1. Install the plugin
2. Open Settings > Keyscript IDE
3. Enter your Keystone server address
4. Click the status bar widget to log in

[Screenshot]
```

**Marketplace title formula:** `[Product] for [IDE]` → "Keyscript IDE for IntelliJ IDEA"

NEVER keyword-stuff the Marketplace title — JetBrains policy prohibits it and it damages trust.

## GitHub README as Distribution Surface

README.md is indexed by search engines and shown on GitHub. Structure for both audiences: evaluators landing from search AND existing users looking for docs.

```markdown
# Keyscript IDE                          ← H1: product name
[Hero description — 1-2 sentences]       ← Indexed by Google
[Install badge]                          ← Action for evaluators
[Screenshot or GIF]                      ← Trust signal

## Features                              ← Anchor for docs seekers
## Quick Start                           ← Activation path
## Configuration                         ← Reference for existing users
## Changelog                             ← Link to CHANGELOG.md
```

Keep the hero + install badge in the first 200 characters — that's what GitHub surfaces in repository cards.

## Update and Re-Engagement Copy

When publishing a new version, copy appears in three places:
1. CHANGELOG.md (detailed)
2. Marketplace "What's New" (summary, ~300 words)
3. In-plugin "Update available" balloon (1-2 sentences)

```kotlin
// In-plugin update balloon — reference to the most compelling new capability
title = "Keyscript IDE updated to v2.1"
body = "Deploy is now available from the gutter. See what's new."
action = BrowseAction("https://plugins.jetbrains.com/plugin/...")
```

## Anti-Patterns

### WARNING: Identical copy across channels

**The Problem:** Copy-pasting the same README paragraph into Marketplace listing and CHANGELOG.

**Why This Breaks:** Each channel has a different reader intent. Marketplace readers are evaluating whether to install. CHANGELOG readers already installed and want to know what changed.

**The Fix:**
- Marketplace: problem → solution → install
- CHANGELOG: what changed → why it's better → upgrade path
- README: what it is → how to start → configuration reference

### WARNING: No visual proof on Marketplace

JetBrains Marketplace listings with no screenshots have significantly lower conversion. Always include:
- At least one screenshot of the plugin running (JCEF preview with a script loaded)
- One screenshot of the tool windows (Workspace + Data Tools)
- Optional: GIF of the run-from-gutter flow

See the **writing-release-notes** skill for CHANGELOG copy patterns.
See the **clarifying-market-fit** skill for positioning the Marketplace listing.
