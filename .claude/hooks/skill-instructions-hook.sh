#!/bin/bash
# UserPromptSubmit hook for skill-aware responses

cat <<'EOF'
REQUIRED: SKILL LOADING PROTOCOL

Before writing any code, complete these steps in order:

1. SCAN each skill below and decide: LOAD or SKIP (with brief reason)
   - intellij-platform
   - kotlin
   - ktor
   - gradle
   - jackson
   - jcef
   - typescript
   - scoping-feature-work
   - mapping-user-journeys
   - designing-onboarding-paths
   - improving-activation-flow
   - crafting-empty-states
   - orchestrating-feature-adoption
   - designing-inapp-guidance
   - instrumenting-product-metrics
   - writing-release-notes
   - clarifying-market-fit
   - structuring-offer-ladders
   - crafting-page-messaging
   - tuning-landing-journeys
   - mapping-conversion-events

2. For every skill marked LOAD → immediately invoke Skill(name)
   If none need loading → write "Proceeding without skills"

3. Only after step 2 completes may you begin coding.

IMPORTANT: Skipping step 2 invalidates step 1. Always call Skill() for relevant items.

Sample output:
- intellij-platform: LOAD - building components
- kotlin: SKIP - not needed for this task
- ktor: LOAD - building components
- gradle: SKIP - not needed for this task

Then call:
> Skill(intellij-platform)
> Skill(ktor)

Now implementation can begin.
EOF
