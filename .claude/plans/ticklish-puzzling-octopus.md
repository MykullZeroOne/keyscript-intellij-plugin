# Keyscript IntelliJ Plugin — Migration Plan

## Context

The current Keyscript IDE is an Electron + React + Monaco app that provides a development environment for Keystone scripts. Maintaining a custom IDE is costly — the user already uses JetBrains products (IntelliJ for batch/Spring Boot, WebStorm for web). Migrating to an IntelliJ plugin eliminates the custom editor, file tree, terminal, and tab management while preserving all Keyscript-specific functionality (preview, proxy, auth, CR completions, script options).

**Goal**: Create a new IntelliJ IDEA plugin project at `/Users/msmith/Documents/Development/UI/keyscript-intellij-plugin/` that replicates 100% of current IDE functionality using native IntelliJ extension points.
The origional IDE can be found at `/Users/msmith/Documents/Development/UI/keyscript-ide-rebuild`

---

## Feature Mapping: What IntelliJ Gives Us for Free

| Current Custom Feature | IntelliJ Native | Action Needed |
|---|---|---|
| Monaco editor + tabs + dirty state | Built-in editor | None |
| File tree (ScriptExplorerPanel) | Project view | None |
| Terminal | Built-in terminal | None |
| Find/replace, go-to-file | Built-in | None |
| Multi-tab editing | Built-in | None |
| Dark theme | User-configurable | None |

## Features We Must Build

| Feature | IntelliJ Mechanism | Priority |
|---|---|---|
| **Script Preview (JCEF)** | `ToolWindowFactory` + `JBCefBrowser` | P0 — non-negotiable |
| **Embedded Proxy** | Ktor HTTP server as project service | P0 — required for preview |
| **Authentication (SSO + password)** | Service + `PasswordSafe` + `DialogWrapper` | P0 |
| **Script Options panel** | `ToolWindowFactory` (right side, always visible) | P0 |
| **Run Configuration** | `RunConfigurationType` + `ProgramRunner` | P0 |
| **CR framework completions** | Bundled `.d.ts` + `CompletionContributor` + Live Templates | P1 |
| **Console capture** | `ToolWindowFactory` + JCEF `onConsoleMessage` | P1 |
| **Network monitor** | `ToolWindowFactory` + proxy event hooks | P1 |
| **Settings** | `PersistentStateComponent` + `Configurable` | P1 |
| **Search panel** | `ToolWindowFactory` | P2 |
| **Table Browser** | `ToolWindowFactory` | P2 |
| **Query Builder** | `ToolWindowFactory` | P2 |

---

## Project Structure

```
keyscript-intellij-plugin/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── src/main/
│   ├── kotlin/com/keyscript/plugin/
│   │   ├── services/
│   │   │   ├── KeyscriptProjectService.kt      # Lifecycle: start proxy, attempt SSO
│   │   │   ├── ProxyServerService.kt            # Embedded Ktor proxy
│   │   │   ├── AuthenticationService.kt         # SSO + password auth
│   │   │   ├── SessionService.kt                # JSESSIONID management
│   │   │   ├── ScriptParameterService.kt        # Person/Account/Instance state
│   │   │   └── NetworkMonitorService.kt         # Request/response capture
│   │   ├── settings/
│   │   │   ├── KeyscriptSettings.kt             # PersistentStateComponent
│   │   │   └── KeyscriptSettingsConfigurable.kt # Settings UI
│   │   ├── completion/
│   │   │   ├── CRCompletionContributor.kt       # CR.* completions
│   │   │   └── CRLiveTemplates.kt               # Live template context
│   │   ├── runconfig/
│   │   │   ├── KeyscriptRunConfigurationType.kt
│   │   │   ├── KeyscriptRunConfiguration.kt
│   │   │   ├── KeyscriptRunConfigurationEditor.kt
│   │   │   ├── KeyscriptRunConfigurationProducer.kt
│   │   │   └── KeyscriptProgramRunner.kt
│   │   ├── toolwindow/
│   │   │   ├── ScriptOptionsToolWindowFactory.kt # Always-visible params panel
│   │   │   ├── PreviewToolWindowFactory.kt       # JCEF preview
│   │   │   ├── ConsoleToolWindowFactory.kt       # Preview console output
│   │   │   ├── NetworkToolWindowFactory.kt       # Network inspector
│   │   │   ├── SearchToolWindowFactory.kt        # Person/Account search
│   │   │   ├── TableBrowserToolWindowFactory.kt
│   │   │   └── QueryBuilderToolWindowFactory.kt
│   │   ├── actions/
│   │   │   ├── RunKeyscriptAction.kt             # Toolbar Run button
│   │   │   ├── LoginAction.kt
│   │   │   └── OpenInChromeAction.kt             # Launch preview in Chrome
│   │   └── proxy/
│   │       ├── KtorProxyServer.kt                # Main Ktor application
│   │       ├── ProxyRoutes.kt                    # Route definitions
│   │       ├── SessionStoreHandler.kt
│   │       ├── RunScriptHandler.kt               # iframe-target.html rendering
│   │       ├── SearchJsonHandler.kt
│   │       ├── UserLoginHandler.kt
│   │       └── CookieInjector.kt                 # JSESSIONID replacement
│   └── resources/
│       ├── META-INF/plugin.xml
│       ├── cr-types/cr-framework.d.ts            # Extracted from cr-types.ts
│       ├── templates/
│       │   ├── iframe-target.html                # From views/iframe-target.html
│       │   └── head-section.html                 # From views/templates/head-section.html
│       └── liveTemplates/Keyscript.xml           # 50+ snippet templates
├── docs/
│   ├── SPEC.md                                   # Full feature specification
│   ├── ARCHITECTURE.md                           # Architecture decisions
│   └── MIGRATION.md                              # Migration guide
└── README.md
```

---

## Critical Implementation Details

### 1. JCEF Preview (Non-Negotiable)

- Use `JBCefBrowser` to render RunScript output in a tool window (right side)
- Flow: Run button → POST params to SessionStore → construct RunScript URL → load in JCEF
- Capture console messages via `CefDisplayHandlerAdapter.onConsoleMessage()`
- Set JSESSIONID cookie via `CefCookieManager.getGlobalManager()` before loading
- Check `JBCefApp.isSupported()` — fallback to "Open in Chrome" if unavailable
- The preview tool window replaces the current split-pane editor approach

### 2. Script Options (Always-Visible Tool Window)

- `ToolWindowFactory` anchored RIGHT, visible by default
- Swing panel with: Instance dropdown, Person Serial field, Account Serial field
- Search buttons trigger SearchJSON via proxy (same XML builder from `keystoneSearch.ts`)
- State stored in `ScriptParameterService` (project-level service)
- **NOT a dialog** — always visible like a sidebar tab

### 3. Embedded Proxy (Ktor)

Port the entire Express proxy from `src/main/proxy.ts` (473 lines) to Kotlin/Ktor:
- **SessionStore**: POST handler storing params in memory map, forwarding to Keystone
- **RunScript**: Template rendering with iframe-target.html + head-section.html substitution
- **Cookie injection**: Always REPLACE existing JSESSIONID (the critical bug fix)
- **Catch-all proxy**: Forward unmatched GET/POST to Keystone endpoint
- **Network events**: Fire events consumed by NetworkMonitorService

Key source files to port:
- `src/main/proxy.ts` — proxy routes and setup
- `src/server/keystoneProxy.ts` — detailed proxy handlers (379 lines)
- `views/iframe-target.html` — RunScript HTML template
- `views/templates/head-section.html` — ExtJS + CR library loader

### 4. Authentication

- On project open: attempt Kerberos SSO via Java `javax.security.auth`
- Fallback: `DialogWrapper` with username/password/instance fields
- Store credentials in `PasswordSafe` (replaces Electron safeStorage)
- Share session with proxy via `POST /api/sso-session`

### 5. CR Framework Completions

Two approaches combined:
- **`.d.ts` library**: Extract type declarations from `cr-types.ts` → register as JS library in plugin.xml (requires IntelliJ Ultimate / WebStorm)
- **Live Templates**: Convert 50+ Monaco snippets from `monaco-setup.ts` to `liveTemplates/Keyscript.xml`
- **CompletionContributor**: Context-aware completions for `CR.*` namespace

### 6. Run Configuration

- `KeyscriptRunConfigurationType` — "Keyscript" in run config dropdown
- `KeyscriptRunConfigurationProducer` — auto-creates configs for `.js` files
- `KeyscriptProgramRunner` — executes the SessionStore → RunScript → Preview flow
- Green Run button in toolbar + gutter icon on `.js` files

---

## Implementation Phases

### Phase 1: Foundation (scaffold + proxy + settings)
1. Create Gradle project with IntelliJ plugin SDK
2. `KeyscriptSettings` + `KeyscriptSettingsConfigurable`
3. `ProxyServerService` with Ktor — catch-all proxy forwarding
4. `RunScriptHandler` with template rendering
5. Verify: proxy starts, forwards to Keystone

### Phase 2: Auth + Preview
6. `AuthenticationService` — manual login dialog
7. `SessionService` — JSESSIONID management
8. `CookieInjector` — JSESSIONID replacement
9. `PreviewPanel` with JCEF + `PreviewToolWindowFactory`
10. Verify: login, run script, see preview in tool window

### Phase 3: Run Flow
11. `ScriptParameterService` + `ScriptOptionsToolWindowFactory`
12. `KeyscriptRunConfigurationType` + `ProgramRunner`
13. `SessionStoreHandler` in proxy
14. Verify: set params in Script Options → click Run → preview renders

### Phase 4: Completions
15. Extract `cr-framework.d.ts` from `cr-types.ts`
16. `CRCompletionContributor` + Live Templates
17. Verify: `CR.` triggers completions, snippets expand

### Phase 5: Developer Tools
18. `ConsoleToolWindowFactory` (JCEF console capture)
19. `NetworkToolWindowFactory` (proxy event display)
20. Kerberos SSO auto-login

### Phase 6: Search + Advanced Features
21. `SearchToolWindowFactory` (Person/Account search)
22. `TableBrowserToolWindowFactory`
23. `QueryBuilderToolWindowFactory`
24. "Open in Chrome" action

---

## Technical Risks

| Risk | Mitigation |
|---|---|
| JCEF not available in all IntelliJ distros | Check `JBCefApp.isSupported()`, fallback to Chrome launcher |
| Ktor classpath conflicts with IntelliJ-bundled libs | Shadow/relocate Ktor deps if needed; test in `runIde` sandbox |
| JavaScript plugin requires Ultimate Edition | Make `.d.ts` library optional; `CompletionContributor` works in Community |
| ExtJS 3 rendering in JCEF | Chromium supports it; ensure same-origin by serving all from proxy |
| Cookie management in JCEF | Use `CefCookieManager.getGlobalManager()` |

---

## Verification Plan

1. **Proxy**: `./gradlew runIde` → plugin starts → proxy accessible at `http://localhost:3000`
2. **Auth**: Login dialog appears → POST to Keystone succeeds → JSESSIONID stored
3. **Preview**: Open `.js` file → click Run → Script Options params sent → preview renders in JCEF tool window
4. **Completions**: Type `CR.` in a `.js` file → see CR framework methods → expand Live Template
5. **Console**: `console.log()` in script → appears in Console tool window
6. **Network**: Proxy requests appear in Network Monitor tool window
7. **Settings**: Change endpoint in Settings → proxy reconnects to new Keystone server

---

## Files to Port from Current Project

| Source File | Target | What to Extract |
|---|---|---|
| `src/main/proxy.ts` | `proxy/KtorProxyServer.kt` + `ProxyRoutes.kt` | All route definitions, proxy setup |
| `src/server/keystoneProxy.ts` | `proxy/*.kt` handlers | SessionStore, RunScript, SearchJSON, login handlers |
| `src/renderer/features/editor/cr-types.ts` | `resources/cr-types/cr-framework.d.ts` | 1387 lines of type declarations |
| `src/renderer/features/editor/monaco-setup.ts` | `resources/liveTemplates/Keyscript.xml` + `completion/CRCompletionContributor.kt` | 50+ snippets, completion logic |
| `src/renderer/utils/keystoneSearch.ts` | `proxy/SearchJsonHandler.kt` | SearchJSON XML builder |
| `views/iframe-target.html` | `resources/templates/iframe-target.html` | RunScript HTML template |
| `views/templates/head-section.html` | `resources/templates/head-section.html` | ExtJS/CR library loader |
| `src/renderer/features/script-options/ScriptOptionsStore.ts` | `services/ScriptParameterService.kt` | Parameter state management |
| `src/renderer/features/settings/SettingsPanel.tsx` | `settings/KeyscriptSettingsConfigurable.kt` | Settings UI layout |
