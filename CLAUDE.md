# Keyscript IDE — IntelliJ Plugin

Development environment for Keystone script creation, ported from the original Electron-based Keyscript IDE to IntelliJ IDEA. Provides a complete IDE experience with JCEF browser preview, embedded proxy server, session authentication, CR framework code completions, and integrated data tools for Keystone table management and query building.

## Tech Stack

| Layer | Technology | Version | Purpose |
|-------|------------|---------|---------|
| IDE | IntelliJ IDEA Ultimate | 2025.1+ | Target platform; Community works with reduced features |
| Language | Kotlin | 2.1.0 | Plugin implementation; required for IntelliJ 2025.1+ platform compatibility |
| JVM Runtime | JBR (JetBrains Runtime) | 21 | Managed by IntelliJ Platform SDK |
| Build System | Gradle | 8.11.1 | Plugin build and packaging |
| HTTP Server | Ktor | 2.3.12 | Lightweight embedded proxy server (CIO engine—no Netty dependency) |
| Browser | JCEF | 2025.1 | Java Chromium Embedded Framework for split-editor preview |
| JSON | Jackson | 2.17.2 | Serialization/deserialization for API payloads |
| IDE Platform SDK | IntelliJ Platform | 2025.1.3 | SDK and bundled plugins (Java, JavaScript) |

## Quick Start

### Prerequisites
- IntelliJ IDEA 2025.1+ (Ultimate recommended, Community works)
- JBR 21 (automatically managed by IntelliJ)
- Gradle 8.11.1 (included in project via wrapper)
- Access to a Keystone server instance

### Development Setup

```bash
# Clone and navigate to project
git clone <repository>
cd keyscript-intellij-plugin

# Build the plugin (creates build/ directory)
./gradlew build

# Run in sandbox IDE (launches test IDE instance with plugin installed)
./gradlew runIde

# Package plugin for distribution
./gradlew buildPlugin
# Output: build/distributions/keyscript-intellij-plugin-2.0.0.zip
```

### First Run in Sandbox IDE
1. Open or create a Keyscript project
2. Go to **Settings > Keyscript IDE** (application-level settings)
3. Configure Keystone server endpoint (e.g., `keystonedev.example.com:8443`)
4. Configure supported instances (comma-separated, e.g., `Development,Test,Production`)
5. Click the status bar widget ("KS: Not Logged In") to authenticate
6. Tool windows appear in right panel (Workspace) and bottom (Data Tools, Diagnostics)
7. Proxy server starts automatically on first script run

### Project Auto-Detection
The plugin automatically detects Keyscript projects containing ANY of:
- `keyscript.bundle.json` at project root
- A `.keyscript` marker file
- Any `*.keyscript.js` file
- Any `*.js` file with `// @keyscript` in the first 5 lines

Zero plugin overhead in non-Keyscript projects (project detection gate prevents activation).

## Project Structure

```
keyscript-intellij-plugin/
├── src/main/kotlin/com/keyscript/plugin/
│   ├── actions/                          # Menu actions (Login, Run, Deploy, Open in Browser)
│   ├── completion/                       # CR framework code completions & live templates
│   ├── preview/                          # JCEF split-editor browser preview
│   ├── project/                          # New Project wizard (templates & module builder)
│   ├── proxy/                            # Embedded Ktor CIO proxy server (routes, cookie injection)
│   ├── runconfig/                        # Run configurations (gutter icons, runner, producer)
│   ├── services/                         # Core project services (14 total):
│   │   ├── SessionService                # JSESSIONID management, heartbeat, auto-relogin
│   │   ├── AuthenticationService         # Login/logout with credentials
│   │   ├── ProxyServerService            # Ktor server lifecycle (lazy startup)
│   │   ├── KeystoneApiClient             # Direct API communication
│   │   ├── PreviewContentService         # HTML/iframe content generation
│   │   ├── NetworkMonitorService         # Captures HTTP request/response events
│   │   ├── RunKeyscriptService           # Script execution lifecycle
│   │   ├── ScriptParameterService        # Script parameter UI & storage
│   │   ├── WorkspaceUiService            # Workspace panel state management
│   │   ├── KeyscriptProjectDetector      # Auto-detection of Keyscript projects
│   │   ├── KeyscriptProjectService       # Project-wide initialization & lifecycle
│   │   ├── DeploymentService             # Deploy scripts to Keystone
│   │   ├── BundleService                 # keyscript.bundle.json management
│   │   └── KeyscriptFileSupport          # File type & icon registration
│   ├── settings/                         # Configuration UI
│   │   ├── KeyscriptSettings             # Application-level settings (singleton)
│   │   ├── KeyscriptSettingsConfigurable # Settings > Keyscript IDE panel
│   │   └── KeyscriptProjectConfigurable  # Settings > Languages & Frameworks > Keyscript IDE
│   ├── statusbar/                        # Login status bar widget
│   └── toolwindow/                       # Tool window factories & panels:
│       ├── KeyscriptWorkspaceToolWindowFactory    # Run Options + Session
│       ├── KeyscriptDataToolsToolWindowFactory    # Search, Table Browser, Query Builder
│       └── KeyscriptDiagnosticsToolWindowFactory  # Console + Network Monitor
│
├── src/main/resources/
│   ├── META-INF/plugin.xml               # Plugin descriptor (extensions, services, actions)
│   ├── cr-types/                         # TypeScript definitions for CR framework
│   ├── liveTemplates/Keyscript.xml       # Keyscript code snippets/templates
│   ├── scripts/                          # Sample scripts & test apps
│   └── templates/                        # HTML templates for preview iframe
│
├── js-lib/                               # JavaScript library (bundled static assets)
│   ├── src/                              # JS/TS source
│   └── dist/                             # Compiled & minified JS (ExtJS, CR framework)
│
├── build.gradle.kts                      # Gradle build configuration
├── settings.gradle.kts                   # Gradle settings (rootProject.name)
├── gradle/wrapper/                       # Gradle wrapper (ensures consistent Gradle version)
├── README.md                             # User-facing documentation
├── CHANGELOG.md                          # Version history & feature log
└── .idea/                                # IntelliJ IDE configuration (gitignored)
```

## Architecture Overview

The plugin uses a **service-oriented architecture** with clear separation of concerns. The core innovation is an **embedded Ktor HTTP proxy** that bridges the JCEF preview browser and the Keystone server.

### The Proxy Pattern (Why This Matters)

In a browser, cross-origin requests are blocked by CORS unless the server explicitly allows them. The original Electron IDE avoided this by running a local proxy. This plugin replicates that architecture:

```
┌─────────────┐         ┌──────────────────┐         ┌────────────┐
│  JCEF iframe│         │  Ktor Proxy      │         │  Keystone  │
│ (localhost) │────────▶│ (cookie inject)  │────────▶│  Server    │
│             │◀────────│  (routes, logs)  │◀────────│  (API)     │
└─────────────┘         └──────────────────┘         └────────────┘
  Script calls          Middleware adds
  DirectXML()           JSESSIONID + logs
```

### How It Works

1. Scripts run in a JCEF iframe at `http://localhost:{port}/{instance}/Keyscript_IDE/RunScript`
2. The CR framework makes AJAX calls (e.g., `DirectXMLPostJSON`) to relative URLs
3. The proxy intercepts these requests, injects the `JSESSIONID` cookie, and forwards to Keystone
4. Responses flow back through the proxy to the iframe
5. Network events are captured and displayed in the Diagnostics panel

This approach **avoids CORS workarounds entirely** and provides transparent session management.

### Service Lifecycle

- **Project Detection**: Plugin auto-activates only for Keyscript projects (zero overhead in non-Keyscript projects)
- **Lazy Initialization**: Proxy server starts on first script run, not at IDE startup
- **Session Management**: `SessionService` maintains `JSESSIONID` and monitors validity with periodic heartbeat checks
- **Credential Storage**: Uses IntelliJ PasswordSafe (secure, OS-integrated; replaces Electron's safeStorage)
- **Thread Safety**: All UI updates happen on the EDT (Event Dispatch Thread); background tasks use `runAsync`

### Tool Windows

Three grouped tool windows appear only in Keyscript projects:

| Window | Location | Contents |
|--------|----------|----------|
| **Workspace** | Right | Run Options (script parameters) + Session info |
| **Data Tools** | Bottom | Search, Table Browser, Query Builder tabs |
| **Diagnostics** | Bottom | Console output + Network Monitor |

## Services (14 Core Components)

| Service | Purpose | Key Patterns |
|---------|---------|--------------|
| **SessionService** | JSESSIONID lifecycle, heartbeat, auto-relogin | Listener pattern, `runAsync` |
| **AuthenticationService** | Login/logout, credential validation | PasswordSafe integration |
| **ProxyServerService** | Ktor server lifecycle, lazy startup | Disposable pattern, singleton |
| **KeystoneApiClient** | Direct API calls, request/response handling | Jackson deserialization, error handling |
| **PreviewContentService** | HTML/iframe content generation | Template rendering |
| **NetworkMonitorService** | Captures HTTP request/response events | Event log, filtering |
| **RunKeyscriptService** | Script execution lifecycle, result handling | Process management |
| **ScriptParameterService** | Script parameter UI & storage | PersistentStateComponent |
| **WorkspaceUiService** | Workspace panel state management | Listener notifications |
| **KeyscriptProjectDetector** | Auto-detection of Keyscript projects | File pattern matching |
| **KeyscriptProjectService** | Project-wide initialization & lifecycle | Disposable, lazy gates |
| **DeploymentService** | Deploy scripts to Keystone | API integration |
| **BundleService** | `keyscript.bundle.json` parsing & management | Jackson configuration |
| **KeyscriptFileSupport** | File type & icon registration | Language support |

## Key Modules

| Module | Location | Purpose | When to Modify |
|--------|----------|---------|----------------|
| **services** | `services/` | Core business logic & state management | Adding features, session management, API integration |
| **proxy** | `proxy/` | HTTP proxy routing, middleware, cookie injection | Changing proxy routes, request/response interception |
| **runconfig** | `runconfig/` | Run configurations, gutter icons, script execution | Modifying how scripts are executed |
| **completion** | `completion/` | Code completions & live templates for CR framework | Adding new CR APIs or templates |
| **preview** | `preview/` | JCEF split-editor browser preview | Changing preview behavior, browser integration |
| **toolwindow** | `toolwindow/` | UI panels for all three tool windows | Modifying UI, adding new panels |
| **settings** | `settings/` | Application & project configuration UI | Adding new settings, persistence logic |
| **project** | `project/` | New Project wizard, templates, module builder | Adding new project templates |
| **actions** | `actions/` | Menu actions (Login, Run, Deploy, etc.) | Adding new menu actions, shortcuts |
| **statusbar** | `statusbar/` | Login status indicator widget | Changing status bar display |

## Development Guidelines

### Code Style

**File Naming:**
- Kotlin files use **PascalCase** (e.g., `SessionService.kt`, `LoginStatusBarWidgetFactory.kt`)
- Packages are **lowercase** (e.g., `actions`, `completion`, `services`)
- Service files follow pattern: `[Name]Service.kt`
- UI components: `[Name]Panel.kt` or `[Name]ToolWindowFactory.kt`

**Code Naming (Inside Files):**
- **Classes/Interfaces**: PascalCase (`class SessionService`, `interface IProxyServer`)
- **Functions**: camelCase (`fun startSession()`, `fun handleLogin()`)
- **Variables**: camelCase (`val project: Project`, `var sessionId: String`)
- **Constants**: SCREAMING_SNAKE_CASE (`val MAX_RETRIES = 3`, `val DEFAULT_PORT = 3000`)
- **Private fields**: `private` modifier or underscore prefix (`_listeners`, `_statusBar`)
- **Boolean variables**: `is`/`has`/`should` prefix (`isLoggedIn`, `hasValidSession`, `shouldUpdate`)

**Import Order:**
1. IntelliJ platform imports (`com.intellij.*`)
2. Kotlin standard library (`kotlin.*`, `kotlinx.*`)
3. Third-party imports (`io.ktor.*`, `com.fasterxml.*`)
4. Internal plugin imports (`com.keyscript.plugin.*`)

**Service Declaration Pattern:**
```kotlin
@Service(Service.Level.PROJECT)  // PROJECT-scoped or APP for application services
class MyService(private val project: Project) : Disposable {
    companion object {
        fun getInstance(project: Project): MyService = project.getService(MyService::class.java)
    }

    override fun dispose() {
        // Cleanup here
    }
}
```

### Architecture Patterns

**State Management:**
- Services use listener pattern with `CopyOnWriteArrayList` for thread-safe notifications
- State changes notify listeners (e.g., SessionService notifies status bar and workspace)
- Example: `session.addListener { /* update UI */ }`

**Async Operations:**
- Use `runAsync` for long-running operations (e.g., API calls, proxy startup)
- Use `runBlocking` in services for synchronous operations where needed (carefully)
- Use Ktor's async/await for non-blocking HTTP client operations

**Error Handling:**
- Log errors via IntelliJ Logger: `Logger.getInstance(::class.java)`
- Show user-facing errors via NotificationGroupManager
- Gracefully degrade functionality (e.g., if proxy fails, still load project without preview)

**Testing:**
- Project has no test suite yet; tests should cover:
  - Service lifecycle (setup, teardown, disposal)
  - Proxy routing & cookie injection
  - Session management (heartbeat, auto-relogin)
  - UI state synchronization
- Use IntelliJ plugin test fixtures (`MockProject`, `MockPsiElement`, etc.)

### Commit Conventions

Follow **Conventional Commits** format:
```
type(scope): description

feat(proxy): add cookie injection middleware
fix(session): handle session timeout gracefully
docs(readme): clarify setup steps
refactor(services): extract AuthService from SessionService
test(runconfig): add executor tests
```

Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`

## Available Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build plugin (outputs to `build/distributions/`) |
| `./gradlew runIde` | Launch sandbox IDE with plugin installed |
| `./gradlew buildPlugin` | Package plugin as ZIP for distribution |
| `./gradlew clean` | Clean build artifacts |
| `./gradlew test` | Run unit tests (none configured yet) |

## Configuration

### Application Settings (Settings > Keyscript IDE)
```kotlin
var keystoneServer: String           // Proxy endpoint (e.g., keystonedev.example.com:8443)
var supportedInstances: String       // Comma-separated (e.g., Development,Test,Production)
var proxyPort: Int                   // Local proxy port (default: 3000)
var servicePort: Int                 // Device service port (default: 1337)
```

### Project Settings (Settings > Languages & Frameworks > Keyscript IDE)
```kotlin
var enabled: Boolean                 // Enable/disable Keyscript support for this project
```

## Extension Points (plugin.xml)

| Extension Point | Implementation | Purpose |
|-----------------|----------------|---------|
| `applicationService` | KeyscriptSettings | Application-level configuration |
| `projectService` | SessionService, AuthenticationService, etc. (14 total) | Project-scoped services |
| `projectConfigurable` | KeyscriptProjectConfigurable | Per-project settings UI |
| `applicationConfigurable` | KeyscriptSettingsConfigurable | Application settings UI |
| `toolWindow` | 3 tool window factories | Tool windows for this project type |
| `configurationType` | KeyscriptRunConfigurationType | Run configuration type |
| `programRunner` | KeyscriptProgramRunner | Script execution handler |
| `runLineMarkerContributor` | KeyscriptRunLineMarkerContributor | Gutter play button |
| `completion.contributor` | CRCompletionContributor | Code completions for CR framework |
| `fileEditorProvider` | KeyscriptSplitEditorProvider | Split-editor browser preview |
| `liveTemplateContext` | KeyscriptTemplateContext | Live template scope |
| `statusBarWidgetFactory` | LoginStatusBarWidgetFactory | Status bar widget |
| `moduleBuilder` | KeyscriptModuleBuilder | New Project wizard |

## Performance & Security

### Performance
- Proxy starts **lazily** (on first script run) to avoid IDE startup overhead
- Project detection **gate** prevents activation in non-Keyscript projects
- Network monitoring is **opt-in** (captured, not logged by default)

### Security
- Credentials stored via **IntelliJ PasswordSafe** (encrypted, OS-integrated)
- `JSESSIONID` injected only into proxy-managed requests (never logged)
- No credentials displayed in UI (only username on login)

### Compatibility
- Plugin supports IntelliJ build versions **251–253.*** (IDEA 2025.1 through 2025.3 beta)
- Community Edition works but with reduced features
- Language-independent; works alongside Java, Python, Go, etc.

## Debugging & Troubleshooting

### Run in Sandbox IDE
```bash
./gradlew runIde
```
All logs appear in the IDE's "Run" tool window. This is the primary debugging entry point.

### Enable Debug Logging
```kotlin
Logger.getInstance(SessionService::class.java).setLevel(Level.DEBUG)
```

### Common Issues

| Issue | Resolution |
|-------|-----------|
| Proxy fails to start | Check port 3000 is available; try different `proxyPort` in settings |
| Session expires silently | SessionService auto-relogins if credentials are saved in PasswordSafe |
| Preview shows blank | Verify Keystone server endpoint in settings; check network connectivity |
| Gutter icons missing | File must be recognized as `.js` with `// @keyscript` or `*.keyscript.js` |
| Build fails with JBR error | Verify `JAVA_HOME` points to JBR 21; check IntelliJ SDK path |

## Skill Usage Guide

When working on tasks involving these core technologies, invoke the corresponding skill first. Each skill provides patterns, workflows, and real code examples from this codebase.

### When to Load Which Skill

| Skill | Load When | Key Files |
|-------|-----------|-----------|
| **intellij-platform** | Adding services, actions, extensions, settings | `SKILL.md` → patterns → workflows |
| **kotlin** | Implementing services, async code, null safety | Listener pattern, EDT threading, sealed classes |
| **ktor** | Modifying proxy routes, cookie injection, HttpClient | Server/client config, route organization |
| **gradle** | Changing build configuration, adding dependencies | `build.gradle.kts`, dependency versions |
| **jackson** | Parsing API responses, JSON serialization | Data class patterns, ObjectNode builder |
| **jcef** | Modifying browser preview, JavaScript messaging | Browser lifecycle, console capture, split editor |

### Example: Add a New Service

1. Load `intellij-platform` skill → check patterns.md for service declaration
2. Load `kotlin` skill → check patterns for async operations
3. Create file: `src/main/kotlin/com/keyscript/plugin/services/MyService.kt`
4. Follow service pattern from skill references
5. Register in `plugin.xml`
6. Run `./gradlew runIde` to test

### Example: Modify the Proxy

1. Load `ktor` skill → check workflows for adding new routes
2. Open `src/main/kotlin/com/keyscript/plugin/proxy/`
3. Add your route using the template from skill references
4. Run `./gradlew runIde` and test with the sandbox IDE
5. Check Diagnostics panel for network log

## Resources

- **README.md**: User-facing feature overview
- **CHANGELOG.md**: Version history and announcements
- **plugin.xml**: Complete extension registration
- **IntelliJ Platform Docs**: https://plugins.jetbrains.com/docs/intellij/
- **Ktor Documentation**: https://ktor.io
- **Keystone Server**: Internal docs at `keystonedev.example.com`

## Next Steps

After this CLAUDE.md:
1. **Rules** (`.claude/rules/*.md`): Coding conventions, architecture patterns, PR checklist
2. **Skills** (`.claude/skills/*/SKILL.md`): Detailed workflow guides with real code patterns


## Skill Usage Guide

When working on tasks involving these technologies, invoke the corresponding skill:

| Skill | Invoke When |
|-------|-------------|
| gradle | Configures build system, dependency management, and plugin packaging |
| kotlin | Implements IntelliJ plugin services, async operations, and Kotlin patterns |
| intellij-platform | Registers extensions, services, tool windows, and IDE integrations |
| jcef | Integrates Java Chromium Embedded Framework for browser preview |
| ktor | Manages embedded HTTP proxy server and request routing |
| jackson | Handles JSON serialization and API payload deserialization |
| mapping-user-journeys | Maps in-app journeys and identifies friction points in code |
| typescript | Types CR framework definitions and JavaScript library components |
| designing-onboarding-paths | Designs onboarding paths, checklists, and first-run UI |
| orchestrating-feature-adoption | Plans feature discovery, nudges, and adoption flows |
| instrumenting-product-metrics | Defines product events, funnels, and activation metrics |
