# Engagement & Adoption — Release Note Writing Guide

This reference covers how to write release note entries for features that improve daily workflows,
increase feature depth, and serve power users of the Keyscript IDE plugin.

---

## What Counts as Engagement/Adoption Content

Engagement content describes improvements to features users already have access to, or new
capabilities that deepen an existing workflow. In Keyscript IDE, the primary engagement surfaces are:

- **Data Tools panel** — Table Browser, Query Builder, Search tab
- **Diagnostics panel** — Network Monitor, Console
- **Script execution** — Run configurations, gutter icons, JCEF preview
- **Code intelligence** — CR framework completions, live templates
- **Deployment** — DeployAction, BundleAction, keyscript.bundle.json

---

## Writing Patterns

### Pattern 1 — Feature depth improvement

Describe what the user can now do that they could not (or could not do easily) before.

```
- **Table Browser**: The Record Operations tab now generates CRUD templates
  (View, Insert, Update, Delete) in both XML and JavaScript with a single click,
  replacing the need to hand-write boilerplate for each table operation.
```

### Pattern 2 — Workflow shortcut

Name the specific action (menu item, keyboard shortcut, gutter icon) and the workflow it
accelerates.

```
- **Gutter Run Icon**: A green play button now appears on line 1 of every Keyscript
  file, matching the familiar Java main-method pattern. Click it (or press
  Ctrl+Shift+F10) to execute the script without leaving the editor.
```

### Pattern 3 — Data Tools improvement

For Query Builder and Table Browser entries, name the specific sub-tab or interaction that changed.

```
- **Query Builder**: Context-aware "Add..." menus now show only valid subelements
  for the selected node — for example, `field` appears only under `record`, and
  `parameter` only under `search`. Previously all 9 subelements were always shown.
```

### Pattern 4 — Completions and templates

State which namespace or pattern is now covered and give a concrete example of what autocompletes.

```
- **CR Completions**: Added `Ext.*` namespace completions (Ext.Msg, Ext.Ajax, Ext.each,
  and 12 others) alongside existing `CR.*` completions. All completions are gated
  to Keyscript files and do not appear in unrelated JavaScript files.
```

---

## Keyscript Engagement Surfaces

### Data Tools Panel

Three tabs: Search, Table Browser, Query Builder.

**Table Browser** entry checklist:
- Name the specific tab affected (Columns, Search Records, Record View, Record Operations).
- Describe what the user can see or generate.
- If filtering/search changed, describe the trigger and scope.

Example:
```
- **Table Browser / Search Records**: The filter dropdown now shows parameter details
  (type, required, description) inline, so you can construct a search without
  cross-referencing external documentation.
```

**Query Builder** entry checklist:
- Note the tree structure default: Query > Sequence > Transaction > Step.
- Name tabs affected (XML Preview, Results, JavaScript generation).
- Describe context-awareness changes (which subelements, under which nodes).

Example:
```
- **Query Builder / JavaScript Tab**: The generated code now uses named CR.XML
  builder methods instead of raw string concatenation, producing output that
  integrates directly with the CR framework API.
```

### Diagnostics Panel

Two tabs: Console, Network Monitor.

**Network Monitor** entry checklist:
- Describe what the master list now shows (method, path, status, duration).
- Describe what the detail view now shows (headers, request body, response body).
- Note correlation behavior (how request and response are linked by event ID).

Example:
```
- **Network Monitor**: Request and response entries are now correlated by event ID,
  so selecting a POST request in the list expands its matching response body in the
  detail pane. Previously, requests and responses appeared as separate unlinked rows.
```

### Script Execution

**Run configuration** entry checklist:
- Name the trigger (gutter icon, keyboard shortcut, context menu item, run config).
- State the file type or marker required (`@keyscript`, `.keyscript.js`).
- Describe what the Run Options / Workspace panel shows during execution.

Example:
```
- **Run Configuration**: Auto-generated run configurations now include the script
  parameter set saved in the Workspace panel's Run Options tab, so repeated runs
  use the last-used parameters without re-entry.
```

### Completions

**CR framework** completions cover five namespaces:
- `CR.XML` — XML builder methods
- `CR.Core` — core utilities
- `CR.Login` — authentication helpers
- `CR.Script` — script lifecycle
- `CR.JSON` — JSON utilities

**Ext** completions cover ExtJS components used in Keyscript UIs.

When writing completion entries, give at least one concrete autocomplete example so readers
understand the scope of coverage:

```
- **CR.XML Completions**: Added instance method completions for XML objects:
  `addContainer`, `addText`, `addOption`, `addElement`. These appear after any
  variable typed as a CR.XML result object.
```

---

## Tone for Engagement Entries

- Focus on what the user accomplishes faster or more accurately, not on the implementation.
- Use the exact panel, tab, or menu name so users can find the feature immediately.
- For power-user entries, it is acceptable to use slightly more technical language
  (e.g., "CR.XML API", "JSESSIONID injection") since these users are already familiar with
  the Keystone ecosystem.
- Avoid "improved", "enhanced", "better" without a specific qualifier. Always say *how* it
  improved: faster, fewer clicks, new coverage, reduced errors.
