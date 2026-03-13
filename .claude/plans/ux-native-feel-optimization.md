# UX Native Feel & Optimization Plan

## Context

The plugin is functionally complete but uses many raw Swing patterns instead of IntelliJ platform conventions. This plan addresses 15 improvements grouped into parallel work streams. Each stream can be assigned to a `jetbrains-plugin-expert` agent working in an isolated worktree.

**Agent type**: `jetbrains-plugin-expert` for all implementation work
**Parallelization strategy**: 5 independent work streams that touch non-overlapping files

---

## Work Streams (Parallel)

### Stream A: Dialog & Interaction Modernization
**Agent**: `jetbrains-plugin-expert` (worktree isolation)
**Files touched**: `LoginAction.kt`, `InstalledScriptsPanel.kt`, `LoginStatusBarWidgetFactory.kt`
**Estimated effort**: 45 min

#### A1. Replace `JOptionPane` with `Messages` (5 min)
- **File**: `src/main/kotlin/com/keyscript/plugin/actions/LoginAction.kt:39`
- **Current**: `JOptionPane.showConfirmDialog()` for logout confirmation
- **Target**: `Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon())`
- **Also fix in**: `InstalledScriptsPanel.kt` delete confirmation

#### A2. Status bar → popup menu instead of dialog (30 min)
- **File**: `src/main/kotlin/com/keyscript/plugin/statusbar/LoginStatusBarWidgetFactory.kt:69`
- **Current**: Click triggers `LoginAction` which either auto-logins or shows dialog
- **Target**: When logged in, show a `JBPopupMenu` with:
  - Header: "Connected as {username} ({instance})" (non-clickable, bold)
  - Separator
  - "Switch Instance..." → submenu listing instances from settings
  - "Open Settings..." → opens Keyscript settings configurable
  - Separator
  - "Logout" → clears session
- When not logged in, keep current auto-login / dialog behavior
- **Implementation**: Use `JBPopupFactory.getInstance().createListPopup()` or `ActionPopupMenu`

#### A3. Copy feedback with `HintManager` (10 min)
- **Files**: `QueryBuilderToolWindowFactory.kt`, `TableBrowserToolWindowFactory.kt`
- **Current**: Silent clipboard copy
- **Target**: After `Toolkit.systemClipboard.setContents()`, show:
  ```kotlin
  HintManager.getInstance().showInformationHint(editor, "Copied to clipboard")
  ```
  For non-editor contexts, use `JBPopupFactory.getInstance().createBalloonBuilder()` anchored to the button

---

### Stream B: Theme & Color Compliance
**Agent**: `jetbrains-plugin-expert` (worktree isolation)
**Files touched**: `LoginAction.kt` (dialog only), `SessionPanel.kt`, `ConsoleToolWindowFactory.kt`, `NetworkToolWindowFactory.kt`
**Estimated effort**: 30 min

#### B1. Replace hard-coded colors with `JBColor` (15 min)
All instances to fix:
- `LoginAction.kt:122` — error label `Color(0xE5, 0x6B, 0x6B)` → `JBColor.RED` or `NamedColorUtil.getErrorForeground()`
- `LoginAction.kt:213` — success color `Color(0x4E, 0xC9, 0xB0)` → `JBColor(Color(0x4E, 0xC9, 0xB0), Color(0x4E, 0xC9, 0xB0))` or use `JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND`
- `NetworkToolWindowFactory.kt` — method colors (POST blue, GET green) → wrap in `JBColor(light, dark)` pairs
- `SessionPanel.kt` — status banner background colors: verify all use `JBColor`

#### B2. Use editor scheme colors for text areas (15 min)
- **Files**: `ConsoleToolWindowFactory.kt`, `InstalledScriptsPanel.kt`
- **Current**: Some text areas use `EditorColorsManager` (good), verify all do
- **Target**: All read-only `JBTextArea` instances that display code should use:
  ```kotlin
  val scheme = EditorColorsManager.getInstance().globalScheme
  background = scheme.defaultBackground
  foreground = scheme.defaultForeground
  font = JBUI.Fonts.create(Font.MONOSPACED, scheme.editorFontSize)
  ```

---

### Stream C: Empty States & Table Modernization
**Agent**: `jetbrains-plugin-expert` (worktree isolation)
**Files touched**: `SearchToolWindowFactory.kt`, `TableBrowserToolWindowFactory.kt`, `NetworkToolWindowFactory.kt`, `ConsoleToolWindowFactory.kt`, `EmptyStatePanel.kt`
**Estimated effort**: 1.5 hr

#### C1. Add `StatusText` to all `JBTable` instances (30 min)
Replace `CardLayout` empty/content switching with native `JBTable.getEmptyText()`:
- **NetworkPanel**: `table.emptyText.setText("No network activity captured").appendLine("Requests will appear here when scripts are run", SimpleTextAttributes.GRAYED_ATTRIBUTES)`
- **SearchPanel**: `table.emptyText.setText("Search for persons or accounts above")`
- **TableBrowser columns table**: `table.emptyText.setText("Select a table from the sidebar")`
- **InstalledScriptsPanel**: `table.emptyText.setText("No scripts loaded").appendLine("Search or refresh to load scripts", SimpleTextAttributes.GRAYED_ATTRIBUTES)`
- Remove the corresponding `CardLayout` + `EmptyStatePanel` wrappers where `StatusText` replaces them

#### C2. Add `StatusText` to `JBList` instances (15 min)
- **TableBrowser sidebar list**: `list.emptyText.setText("No tables available").appendLine("Login to load table metadata")`
- **ScriptOptions search results list**: `list.emptyText.setText("No results")`

#### C3. Keep `EmptyStatePanel` for non-table contexts (15 min)
- **ConsolePanel**: Keep `EmptyStatePanel` (text area, not a table) but ensure it uses `StatusText` style colors
- **SessionPanel**: Keep for login prompt (has action button)
- **Refactor**: Make `EmptyStatePanel` use `SimpleTextAttributes.GRAYED_ATTRIBUTES` colors to match `StatusText` visually

#### C4. Add `TreeSpeedSearch` to Query Builder tree (15 min)
- **File**: `QueryBuilderToolWindowFactory.kt`
- **Current**: Tree has no speed search
- **Target**: `TreeSpeedSearch.installOn(tree)` — enables type-to-filter

#### C5. Add `SpeedSearchUtil` to TableBrowser sidebar (15 min)
- **File**: `TableBrowserToolWindowFactory.kt`
- **Current**: Manual text filter field
- **Target**: Keep the filter field but also add `ListSpeedSearch.installOn(list)` for keyboard-driven filtering

---

### Stream D: Toolbar Modernization
**Agent**: `jetbrains-plugin-expert` (worktree isolation)
**Files touched**: `ConsoleToolWindowFactory.kt`, `NetworkToolWindowFactory.kt`, `InstalledScriptsPanel.kt`, `QueryBuilderToolWindowFactory.kt`
**Estimated effort**: 2 hr

#### D1. Convert Console toolbar to `ActionToolbar` (30 min)
- **Current**: `JPanel(BoxLayout.X_AXIS)` with `JButton("Clear")`
- **Target**:
  ```kotlin
  val clearAction = object : AnAction("Clear", "Clear console", AllIcons.Actions.GC) {
      override fun actionPerformed(e: AnActionEvent) { clearConsole() }
  }
  val group = DefaultActionGroup(clearAction)
  val toolbar = ActionManager.getInstance().createActionToolbar("KeyscriptConsole", group, true)
  toolbar.targetComponent = mainPanel
  add(toolbar.component, BorderLayout.NORTH)
  ```

#### D2. Convert Network toolbar to `ActionToolbar` (30 min)
- Same pattern as D1
- Actions: "Clear All" with `AllIcons.Actions.GC`

#### D3. Convert Installed Scripts toolbar to `ActionToolbar` (30 min)
- Actions: Search (with custom search field via `SearchTextField`), Refresh, separator, Download, Update, Install, Delete
- Use `ActionToolbar` with `Separator` for grouping
- Keep search field as a custom component via `toolbar.addAction()` or adjacent panel

#### D4. Convert Query Builder toolbar to `ActionToolbar` (30 min)
- Actions: Add (with popup submenu), Remove, separator, Verify, Post, separator, Copy XML, HTTP Client
- Use `DefaultActionGroup` with `Separator` instances
- "Add..." uses `ActionGroup` with `isPopup = true` for dropdown

---

### Stream E: Session Panel & Performance
**Agent**: `jetbrains-plugin-expert` (worktree isolation)
**Files touched**: `SessionPanel.kt`, `ScriptOptionsToolWindowFactory.kt`, `KeyscriptSplitEditorProvider.kt`
**Estimated effort**: 1.5 hr

#### E1. Session panel update-in-place instead of rebuild (45 min)
- **Current**: `refresh()` removes all children and rebuilds entire panel
- **Target**: Create labels once in constructor, update text values in `refresh()`:
  ```kotlin
  private val usernameValue = JBLabel()
  private val instanceValue = JBLabel()
  // ... etc

  fun refresh() {
      if (session.isLoggedIn) {
          usernameValue.text = session.username
          instanceValue.text = session.instance
          // ... update other fields
          cardLayout.show(container, "info")
      } else {
          cardLayout.show(container, "empty")
      }
  }
  ```
- Eliminates flicker on session state changes

#### E2. ScriptOptions search debounce (15 min)
- **Current**: Search may trigger on every keystroke via `DocumentListener`
- **Target**: Add a `Timer(500)` debounce like the preview reload timer:
  ```kotlin
  private val searchDebounce = Timer(500) { doSearch() }.apply { isRepeats = false }
  // In DocumentListener:
  searchDebounce.restart()
  ```

#### E3. Preview reload debounce tuning (15 min)
- **File**: `KeyscriptSplitEditorProvider.kt:100`
- **Current**: 300ms debounce
- **Target**: Check if bundle project → use 800ms (esbuild rebuild latency), plain JS → keep 300ms
  ```kotlin
  private val debounceMs = if (BundleService.getInstance(project).findBundleRootFor(java.io.File(file.path)) != null) 800 else 300
  private val reloadTimer = Timer(debounceMs) { previewComponent.reload() }
  ```

#### E4. Lazy table metadata loading (30 min)
- **File**: `TableBrowserToolWindowFactory.kt`
- **Current**: Loads all tables on login
- **Target**: Load on first tab activation:
  ```kotlin
  private var tablesLoaded = false
  // In tab selection listener or selectNotify():
  if (!tablesLoaded && session.isLoggedIn) {
      tablesLoaded = true
      loadTables()
  }
  ```

---

## Execution Plan

```
┌──────────────────────────────────────────────────────────┐
│                    PARALLEL EXECUTION                      │
├──────────┬──────────┬──────────┬──────────┬──────────────┤
│ Stream A │ Stream B │ Stream C │ Stream D │   Stream E   │
│ Dialogs  │ Colors   │ Empty    │ Toolbars │   Session    │
│ & Popups │ & Theme  │ States   │          │   & Perf     │
│          │          │          │          │              │
│ A1: 5m   │ B1: 15m  │ C1: 30m  │ D1: 30m  │ E1: 45m     │
│ A2: 30m  │ B2: 15m  │ C2: 15m  │ D2: 30m  │ E2: 15m     │
│ A3: 10m  │          │ C3: 15m  │ D3: 30m  │ E3: 15m     │
│          │          │ C4: 15m  │ D4: 30m  │ E4: 30m     │
│          │          │ C5: 15m  │          │              │
│          │          │          │          │              │
│ Total:   │ Total:   │ Total:   │ Total:   │ Total:       │
│ 45 min   │ 30 min   │ 1.5 hr   │ 2 hr     │ 1.5 hr      │
└──────────┴──────────┴──────────┴──────────┴──────────────┘
                           │
                           ▼
               ┌───────────────────────┐
               │   SEQUENTIAL MERGE    │
               │                       │
               │ 1. Merge all streams  │
               │ 2. Build verification │
               │ 3. Package plugin     │
               │ 4. Manual smoke test  │
               └───────────────────────┘
```

## Agent Configuration

Each stream launches as:
```
Agent(
    subagent_type = "jetbrains-plugin-expert",
    isolation = "worktree",
    prompt = "<stream-specific prompt with file list and exact changes>"
)
```

All 5 agents run in parallel. After completion:
1. Review each worktree's changes
2. Cherry-pick/merge non-conflicting changes to main
3. Resolve any conflicts (unlikely given file separation)
4. Run `./gradlew build` to verify
5. `./gradlew buildPlugin` to package

## Verification Checklist

After all streams merge:
- [ ] Build succeeds with no warnings
- [ ] Login dialog uses `Messages` not `JOptionPane`
- [ ] Status bar shows popup menu when logged in
- [ ] All hard-coded colors replaced with `JBColor`
- [ ] Tables show `StatusText` when empty (no CardLayout switching)
- [ ] All toolbars use `ActionToolbar`
- [ ] Session panel updates without flicker
- [ ] Query Builder tree supports speed search
- [ ] Copy buttons show feedback balloon
- [ ] Preview debounce is tuned per project type
- [ ] Table browser loads lazily
- [ ] Plugin works in both light and dark themes

## Files Modified Per Stream (Conflict Check)

| File | A | B | C | D | E |
|------|---|---|---|---|---|
| `LoginAction.kt` | A1 | B1 | | | |
| `LoginStatusBarWidgetFactory.kt` | A2 | | | | |
| `InstalledScriptsPanel.kt` | A1 | | C1 | D3 | |
| `SessionPanel.kt` | | B1 | | | E1 |
| `ConsoleToolWindowFactory.kt` | | B2 | C3 | D1 | |
| `NetworkToolWindowFactory.kt` | | B1 | C1 | D2 | |
| `SearchToolWindowFactory.kt` | | | C1 | | |
| `TableBrowserToolWindowFactory.kt` | | | C1,C5 | | E4 |
| `QueryBuilderToolWindowFactory.kt` | A3 | | C4 | D4 | |
| `ScriptOptionsToolWindowFactory.kt` | | | | | E2 |
| `KeyscriptSplitEditorProvider.kt` | | | | | E3 |
| `EmptyStatePanel.kt` | | | C3 | | |

**Conflict zones** (files touched by 2+ streams):
- `LoginAction.kt`: A1 (dialog section) + B1 (color in dialog) → low conflict, different sections
- `InstalledScriptsPanel.kt`: A1 (delete confirm) + C1 (empty text) + D3 (toolbar) → moderate, but different sections
- `ConsoleToolWindowFactory.kt`: B2 (colors) + C3 (empty state) + D1 (toolbar) → moderate, different sections
- `NetworkToolWindowFactory.kt`: B1 (colors) + C1 (empty text) + D2 (toolbar) → moderate, different sections
- `TableBrowserToolWindowFactory.kt`: C1,C5 (empty/search) + E4 (lazy load) → low conflict
- `QueryBuilderToolWindowFactory.kt`: A3 (copy feedback) + C4 (tree search) + D4 (toolbar) → moderate

**Mitigation**: For files with 3+ streams, assign to the stream with the largest change scope and have other streams skip that file. Recommended reassignment:
- `InstalledScriptsPanel.kt` → Stream D owns entirely (toolbar + empty text + confirm dialog)
- `ConsoleToolWindowFactory.kt` → Stream D owns entirely (toolbar + empty state + colors)
- `NetworkToolWindowFactory.kt` → Stream D owns entirely (toolbar + empty text + colors)
- `QueryBuilderToolWindowFactory.kt` → Stream D owns entirely (toolbar + tree search + copy feedback)

This makes Stream D the "big panel rewrite" stream and reduces conflicts to near-zero.

## Revised Stream Ownership (Final)

| Stream | Owns Files | Tasks |
|--------|-----------|-------|
| **A** | `LoginAction.kt` (dialog), `LoginStatusBarWidgetFactory.kt` | A1 (Messages), A2 (popup menu) |
| **B** | `SessionPanel.kt` | B1 (JBColor in session panel only), B2 (verify editor colors) |
| **C** | `SearchToolWindowFactory.kt`, `TableBrowserToolWindowFactory.kt`, `EmptyStatePanel.kt` | C1 (search/table empty text), C3 (EmptyStatePanel refactor), C5 (speed search) |
| **D** | `ConsoleToolWindowFactory.kt`, `NetworkToolWindowFactory.kt`, `InstalledScriptsPanel.kt`, `QueryBuilderToolWindowFactory.kt` | D1-D4 (toolbars), A1 delete confirm, A3 copy feedback, B1 network colors, C1 console/network/installed empty text, C4 tree search |
| **E** | `ScriptOptionsToolWindowFactory.kt`, `KeyscriptSplitEditorProvider.kt` | E1 (session panel update — coordinates with B), E2 (debounce), E3 (preview tuning), E4 (lazy load in TableBrowser — coordinates with C) |

**Cross-stream coordination**:
- E1 (session panel): Stream B does colors, Stream E does update-in-place → E depends on B finishing first, OR E includes color fixes for SessionPanel
- E4 (lazy load): Stream C does empty text for TableBrowser, Stream E does lazy load → can coexist since they touch different methods

**Simplified**: Merge B into E (both small, SessionPanel is shared). Run 4 parallel streams: A, C, D, E.
