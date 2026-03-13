# Keyscript IDE IntelliJ Plugin

Development environment for Keystone script creation, ported from the original Electron-based Keyscript IDE to IntelliJ IDEA. Provides a complete IDE experience with JCEF browser preview, embedded proxy server, session authentication, CR framework code completions, and integrated data tools for Keystone table management and query building.

## Tech Stack

| Layer | Technology | Version | Purpose |
|-------|------------|---------|---------|
| IDE | IntelliJ IDEA Ultimate | 2025.1+ | Target platform; Community works with reduced features |
| Language | Kotlin | 2.1.0 | Plugin implementation; required for IntelliJ 2025.1+ platform compatibility |
| JVM Runtime | JBR (JetBrains Runtime) | 21 | Managed by IntelliJ Platform SDK |
| Build System | Gradle | 8.11.1 | Plugin build and packaging |
| HTTP Server | Ktor | 2.3.12 | Lightweight embedded proxy server (CIO engine—no Netty dependency) |
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

### First Run
1. In sandbox IDE, open/create a Keyscript project
2. Go to **Settings > Keyscript IDE** (application-level settings)
3. Configure Keystone server endpoint and instance names
4. Click the status bar widget ("KS: Not Logged In") to authenticate
5. Tool windows appear in right panel (Workspace) and bottom (Data Tools, Diagnostics)

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

The plugin uses a **service-oriented architecture** with clear separation of concerns:

### Proxy Pattern
The core innovation is an **embedded Ktor HTTP proxy** that bridges the JCEF preview browser and Keystone server:

1. Scripts run inside a JCEF iframe at `http://localhost:{port}/{instance}/Keyscript_IDE/RunScript`
2. The CR framework makes AJAX calls (e.g., `DirectXMLPostJSON`) to relative URLs
3. The proxy intercepts these requests, injects the `JSESSIONID` cookie, and forwards to Keystone
4. Responses flow back through the proxy to the iframe
5. Network events are captured for the Diagnostics panel

This replicates the original Electron IDE architecture without requiring browser CORS workarounds.

### Service Lifecycle
- **Project Detection**: Plugin auto-activates only for Keyscript projects (zero overhead in non-Keyscript projects)
- **Lazy Initialization**: Proxy server starts on first run, not at IDE startup
- **Session Management**: SessionService maintains JSESSIONID and monitors session validity with periodic heartbeat checks
- **Credential Storage**: Uses IntelliJ PasswordSafe (secure, OS-integrated; replaces Electron's safeStorage)

### Tool Windows
Three grouped tool windows only appear in Keyscript projects:
- **Workspace** (right): Run Options (script parameters) + Session tab
- **Data Tools** (bottom): Search, Table Browser, Query Builder tabs
- **Diagnostics** (bottom): Console output + Network Monitor

### Key Modules

| Module | Purpose | Key Files |
|--------|---------|-----------|
| **services** | Core business logic, state management | SessionService, AuthenticationService, ProxyServerService, KeystoneApiClient, RunKeyscriptService |
| **proxy** | HTTP proxy server routing & middleware | KtorProxyServer, ProxyRoutes, CookieInjector |
| **runconfig** | Run configurations, gutter icons, execution | KeyscriptRunConfigurationType, KeyscriptProgramRunner, KeyscriptRunLineMarkerContributor |
| **completion** | Code completions & live templates | CRCompletionContributor, CRLibraryProvider, KeyscriptTemplateContext |
| **preview** | JCEF split-editor browser | KeyscriptSplitEditorProvider, JCEFBrowserPanel |
| **toolwindow** | UI panels for tool windows | WorkspacePanel, TableBrowserPanel, QueryBuilderPanel, DiagnosticsPanel |
| **settings** | Application & project configuration | KeyscriptSettings, KeyscriptSettingsConfigurable |
| **project** | New Project wizard | KeyscriptModuleBuilder, project templates |
| **actions** | Menu actions | LoginAction, RunScriptAction, DeployAction, OpenInBrowserAction |
| **statusbar** | Login status indicator | LoginStatusBarWidget, LoginStatusBarWidgetFactory |

## Development Guidelines

### Code Style

**File Naming:**
- Kotlin files use PascalCase (e.g., `SessionService.kt`, `LoginStatusBarWidgetFactory.kt`)
- Packages are lowercase (e.g., `actions`, `completion`, `services`)
- Service implementations follow pattern: `[Name]Service.kt`
- UI components: `[Name]Panel.kt` or `[Name]ToolWindowFactory.kt`

**Code Naming:**
- **Classes/Interfaces**: PascalCase (`class SessionService`, `interface IProxyServer`)
- **Functions**: camelCase (`fun startSession()`, `fun handleLogin()`)
- **Variables**: camelCase (`val project: Project`, `var sessionId: String`)
- **Constants**: SCREAMING_SNAKE_CASE (`val MAX_RETRIES = 3`, `val DEFAULT_PORT = 3000`)
- **Private fields**: Underscore prefix (`_listeners`, `_statusBar`) or use `private` modifier
- **Boolean variables**: `is`/`has` prefix (`isLoggedIn`, `hasValidSession`)

**Import Order:**
1. IntelliJ platform imports (`com.intellij.*`)
2. Kotlin standard library imports (`kotlin.*`, `kotlinx.*`)
3. Third-party imports (`io.ktor.*`, `com.fasterxml.*`)
4. Internal plugin imports (from `com.keyscript.plugin.*`)
5. Type imports (if using `import type` syntax)

**Service Declaration Pattern:**
```kotlin
@Service(Service.Level.PROJECT)  // or Service.Level.APP for application services
class MyService(private val project: Project) : Disposable {
    // Implementation
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

## Configuration Files

### Application Settings (`Settings > Keyscript IDE`)
```kotlin
// KeyscriptSettings (application-level singleton)
var keystoneServer: String           // Proxy endpoint URL (e.g., keystonedev.example.com:8443)
var supportedInstances: String       // Comma-separated list (e.g., Development,Test,Production)
var proxyPort: Int                   // Local proxy server port (default: 3000)
var servicePort: Int                 // Device service port (default: 1337)
```

### Project Settings (`Settings > Languages & Frameworks > Keyscript IDE`)
```kotlin
// KeyscriptProjectConfigurable (per-project)
var enabled: Boolean                 // Enable/disable Keyscript support for this project
```

### Auto-Detection
Projects are automatically detected if they contain:
- `keyscript.bundle.json` at project root
- A `.keyscript` marker file
- Any `*.keyscript.js` file
- Any `*.js` file with `// @keyscript` in the first 5 lines

## Extension Points (plugin.xml)

The plugin registers these IntelliJ extension points:

| Extension Point | Implementation | Purpose |
|-----------------|----------------|---------|
| `applicationService` | KeyscriptSettings | Application-level configuration |
| `projectService` | SessionService, AuthenticationService, etc. | Project-scoped services (14 total) |
| `projectConfigurable` | KeyscriptProjectConfigurable | Settings UI for per-project config |
| `applicationConfigurable` | KeyscriptSettingsConfigurable | Settings UI for application config |
| `toolWindow` | KeyscriptWorkspaceToolWindowFactory, etc. | Tool window factories (3 total) |
| `configurationType` | KeyscriptRunConfigurationType | Run configuration type |
| `programRunner` | KeyscriptProgramRunner | Execution handler |
| `runLineMarkerContributor` | KeyscriptRunLineMarkerContributor | Gutter play button for Keyscript files |
| `completion.contributor` | CRCompletionContributor | Code completions for CR framework |
| `fileEditorProvider` | KeyscriptSplitEditorProvider | Split-editor browser preview |
| `liveTemplateContext` | KeyscriptTemplateContext | Live template scope (KEYSCRIPT_JS) |
| `statusBarWidgetFactory` | LoginStatusBarWidgetFactory | Status bar login widget |
| `moduleBuilder` | KeyscriptModuleBuilder | New Project wizard |

## Dependencies & Versions

See @build.gradle.kts for full dependency tree:
- **Ktor Server**: 2.3.12 (core, CIO, CORS, content negotiation)
- **Ktor Client**: 2.3.12 (for API calls to Keystone)
- **Jackson**: 2.17.2 (JSON serialization with Kotlin module)
- **IntelliJ Platform**: 2025.1.3 (with bundled Java & JavaScript plugins)

## Key Considerations

### Performance
- Proxy starts lazily (on first script run) to avoid IDE startup overhead
- Project detection gate prevents plugin activation in non-Keyscript projects
- Network monitoring is opt-in (captured in Diagnostics panel, not logged by default)

### Security
- Credentials stored via IntelliJ PasswordSafe (encrypted, OS-integrated)
- JSESSIONID injected only into proxy-managed requests
- No credentials logged or displayed in UI (except username on login)

### Compatibility
- Plugin supports IntelliJ build versions 251–253.* (IDEA 2025.1 through 2025.3 beta)
- Community Edition works but with reduced IDE features (no Java tooling, etc.)
- Plugin is language-independent; works alongside Java, Python, Go, etc.

## Debugging & Troubleshooting

### Run in Sandbox IDE
```bash
./gradlew runIde
```
This launches a test IDE instance with the plugin installed. All logs appear in the IDE's "Run" panel.

### Enable Debug Logging
In Settings > Keyscript IDE, enable verbose logging:
```kotlin
Logger.getInstance(SessionService::class.java).setLevel(Level.DEBUG)
```

### Common Issues

| Issue | Resolution |
|-------|-----------|
| Proxy fails to start | Check port 3000 is available; increase `proxyPort` in settings |
| Session expires silently | SessionService auto-relogins if credentials are saved; check PasswordSafe |
| Preview shows blank | Check Keystone server endpoint in settings; verify network connectivity |
| Gutter icons don't appear | File must be recognized as `.js` with Keyscript markers (`// @keyscript` or `*.keyscript.js`) |

## Resources

- **README.md**: User-facing feature overview and quick-start guide
- **CHANGELOG.md**: Version history and major feature announcements
- **plugin.xml**: Complete extension point registration and plugin metadata
- **Ktor Documentation**: https://ktor.io (embedded server & client)
- **IntelliJ Platform**: https://plugins.jetbrains.com/docs/intellij/
- **Keystone Server**: Internal documentation at `keystonedev.example.com`

## Next Steps in Documentation

After this CLAUDE.md:
1. **Rules** (.claude/rules/*.md): Coding conventions, architecture patterns, PR checklist
2. **Skills** (.claude/skills/*/SKILL.md): Workflow guides (debugging, adding features, testing)


## Skill Usage Guide

When working on tasks involving these technologies, invoke the corresponding skill:

| Skill | Invoke When |
|-------|-------------|
| gradle | Configures build, dependencies, packaging for plugin distribution |
| intellij-platform | Develops plugin extensions, services, and IDE integration points |
| ktor | Builds embedded HTTP proxy server for request routing and interception |
| jackson | Serializes and deserializes JSON for APIs and configuration |
| kotlin | Implements plugin services, components, and UI in Kotlin |
| jcef | Integrates Java Chromium Embedded Framework for browser preview |
| scoping-feature-work | Breaks features into MVP slices and acceptance criteria |
| typescript | Develops type definitions and bundled JavaScript assets |
| designing-onboarding-paths | Designs onboarding paths, checklists, and first-run UI |
| improving-activation-flow | Optimizes activation steps and time-to-value milestones |
| mapping-user-journeys | Maps in-app journeys and identifies friction points in code |
| instrumenting-product-metrics | Defines product events, funnels, and activation metrics |
| crafting-empty-states | Creates empty states and onboarding affordances |
| orchestrating-feature-adoption | Plans feature discovery, nudges, and adoption flows |
| designing-inapp-guidance | Builds tooltips, tours, and contextual guidance |
| clarifying-market-fit | Aligns ICP, positioning, and value narrative for on-page messaging |
| writing-release-notes | Drafts release notes tied to shipped features |
| structuring-offer-ladders | Frames plan tiers, value ladders, and upgrade logic |
| tuning-landing-journeys | Improves landing page flow, hierarchy, and conversion paths |
| crafting-page-messaging | Writes conversion-focused messaging for pages and key CTAs |
| mapping-conversion-events | Defines funnel events, tracking, and success signals |
