# Keyscript IDE — IntelliJ Plugin

Development environment for Keystone scripts, ported from the original Electron-based Keyscript IDE.
Provides JCEF preview, embedded proxy, authentication, script parameters, CR framework completions,
and developer tools — all inside IntelliJ IDEA.

## Requirements

- IntelliJ IDEA 2025.1+ (Ultimate recommended, Community works with reduced features)
- JBR 21 (JetBrains Runtime)
- Access to a Keystone server instance

## Quick Start

### New Project
1. **File > New > Project > Keyscript**
2. Select a template (Vanilla JS, React + Keystone, React Standalone, or Blank)
3. Configure your Keystone connection in **Settings > Keyscript IDE** (application-level settings)
4. Click the status bar widget ("KS: Not Logged In") to authenticate

### Existing Project
1. Open your project in IntelliJ
2. Go to **Settings > Languages & Frameworks > Keyscript IDE**
3. Check **"Enable Keyscript IDE support for this project"**
4. Re-open the project for tool windows to appear

Or — the plugin auto-detects projects that contain:
- `keyscript.bundle.json` at the project root
- A `.keyscript` marker file
- Any `*.keyscript.js` file or JS file with `// @keyscript` in the first 5 lines

## Features

### Script Development
- **Split editor preview**: JCEF browser panel alongside your code
- **Run gutter icon**: Green play button on Keyscript files (like Java's main method)
- **Run shortcut**: `Ctrl+Shift+F10` to execute the current script
- **CR framework completions**: `CR.XML`, `CR.Core`, `CR.Login`, `CR.Script`, `Ext.*`
- **Live templates**: Common Keyscript patterns

### Tool Windows

| Window | Location | Contents |
|--------|----------|----------|
| **Keyscript Workspace** | Right | Run Options (script parameters) + Session info |
| **Keyscript Data Tools** | Bottom | Search, Table Browser, Query Builder |
| **Keyscript Diagnostics** | Bottom | Console output + Network monitor |

### Table Browser
- Browse all Keystone tables with live filtering
- View column metadata (types, constraints, references)
- Search records using available filters
- View individual records by serial
- Generate CRUD templates (View/Insert/Update/Delete) in XML and JavaScript

### Query Builder
- Visual tree editor for Corelation XML queries
- 9 step subelements: search, record, feeReview, postingRequest, contentsFrom, tableList, monetary, field, parameter
- XML preview, verify mode, post execution
- JavaScript code generation using CR.XML API

### Network Monitor
- Expandable request/response detail view
- Color-coded HTTP methods and status codes
- Request and response body inspection

## Configuration

### Application Settings (Settings > Keyscript IDE)
- **Keystone Server**: Proxy endpoint URL (e.g., `keystonedev.example.com:8443`)
- **Supported Instances**: Comma-separated list (e.g., `Development,Test,Production`)
- **Proxy Port**: Local proxy server port (default: 3000)
- **Service Port**: Device service port (default: 1337)

### Project Settings (Settings > Languages & Frameworks > Keyscript IDE)
- Enable/disable Keyscript support for the current project

## Project Structure

```
keyscript-intellij-plugin/
├── build.gradle.kts                    # Build configuration (Kotlin 2.1.0, IntelliJ 2025.1.3)
├── src/main/kotlin/com/keyscript/plugin/
│   ├── actions/                        # Menu actions (Login, Run, Open in Chrome)
│   ├── completion/                     # CR/Ext code completions + live templates
│   ├── preview/                        # JCEF split editor preview
│   ├── project/                        # New Project wizard (KeyscriptModuleBuilder)
│   ├── proxy/                          # Embedded Ktor CIO proxy server
│   ├── runconfig/                      # Run configurations + gutter icon
│   ├── services/                       # Core services (auth, session, proxy, detection)
│   ├── settings/                       # Application + project settings
│   ├── statusbar/                      # Login status bar widget
│   └── toolwindow/                     # All tool window panels
├── src/main/resources/
│   ├── META-INF/plugin.xml             # Plugin descriptor
│   ├── cr-types/                       # CR framework TypeScript definitions
│   ├── liveTemplates/                  # Keyscript live template definitions
│   ├── scripts/                        # Sample scripts + test apps
│   └── templates/                      # HTML templates for preview iframe
└── CHANGELOG.md
```

## Building

```bash
# Requires JBR 21
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-jbr

# Build
./gradlew build

# Run in sandbox IDE
./gradlew runIde

# Package plugin
./gradlew buildPlugin
```

## How It Works

The plugin embeds a Ktor HTTP server (CIO engine) that acts as a proxy between
the JCEF browser preview and the Keystone server. This replicates the architecture
of the original Electron IDE:

1. Scripts run inside a JCEF iframe loaded from `http://localhost:{port}/{instance}/Keyscript_IDE/RunScript`
2. The CR framework makes relative AJAX calls (e.g., `DirectXMLPostJSON`)
3. The proxy intercepts these, injects the JSESSIONID cookie, and forwards to Keystone
4. Responses flow back through the proxy to the iframe

The proxy also serves bundled static resources (ExtJS, CR framework JS, templates)
and captures network events for the diagnostics panel.
