# Keyscript IDE Plugin — Changelog

## [2.0.0] — 2026-03-11

Major rewrite from the original `com.revfcu.keyscript` prototype. The plugin is now a full-featured
Keyscript development environment for IntelliJ-based IDEs, ported from the original Electron IDE.

### Architecture
- **Package rename**: `com.revfcu.keyscript` → `com.keyscript.plugin`
- **Platform**: IntelliJ 2025.1.3 (builds 251–253.*)
- **Kotlin**: Upgraded from 1.9.25 to 2.1.0 (required for 2025.1+ platform compatibility)
- **Proxy engine**: Switched from Netty to CIO for fast, lightweight startup
- **Lazy initialization**: Proxy server starts on first use, not at IDE startup
- **Project detection**: Plugin only activates for Keyscript projects (zero overhead otherwise)

### New Project Setup
- **New Project wizard**: File > New > Project > Keyscript with 4 templates:
  - Vanilla JS + Keystone, React + Keystone, React Standalone, Blank Script
- **Language & Frameworks settings**: Settings > Languages & Frameworks > Keyscript IDE
  to enable/disable per-project
- **Auto-detection**: Projects with `keyscript.bundle.json`, `.keyscript`, or `*.keyscript.js`
  files are detected automatically

### Tool Windows
- **Keyscript Workspace** (right panel): Run Options tab + Session tab
- **Keyscript Data Tools** (bottom panel): Search, Table Browser, Query Builder tabs
- **Keyscript Diagnostics** (bottom panel): Console + Network tabs
- All tool windows only appear in Keyscript projects (`isApplicable` gating)

### Table Browser
- Browse all Keystone tables with live filter
- **Columns tab**: View column metadata (name, type, nullable, references)
- **Search Records tab**: Filter dropdown with parameter details, search execution,
  XML/JS template preview with copy buttons
- **Record View tab**: Double-click search results to view full record fields
- **Record Operations tab**: CRUD templates (View/Insert/Update/Delete) with
  XML and JS code generation

### Query Builder
- Tree-based query editor with default structure (Query > Sequence > Transaction > Step)
- **9 step subelements**: search, record, feeReview, postingRequest, contentsFrom,
  tableList, monetary, field, parameter
- Context-aware "Add..." dropdown (field only under record, parameter only under search)
- XML Preview, Results, and JavaScript generation tabs
- Verify and Post execution modes

### Network Monitor
- Master-detail split pane with request list and expandable detail view
- Color-coded method (POST=blue, GET=green) and status (2xx=green, 4xx+=red)
- Request/response body display in monospace text areas
- Request-response correlation by event ID

### Run Configuration
- **Gutter run icon**: Green play button on line 1 of Keyscript files (like Java main)
- **Run configuration producer**: Auto-creates configs for `@keyscript` / `.keyscript.js` files
- **Keyboard shortcut**: Ctrl+Shift+F10 to run current file
- Context menu entries in editor and project view

### Proxy Server
- Embedded Ktor CIO server replicating the original Electron proxy
- `/Keyscript_IDE/` path stripping for iframe-relative URLs (fixes 404 on script execution)
- JSESSIONID cookie injection on all proxied requests
- SessionStore parameter interception and mapping
- Bundled static resource serving with instance-prefix support
- Network event capture for diagnostics

### Authentication
- Login dialog with Keystone server, instance, device ID, username/password
- Dialog closes reliably on success (uses `close(OK_EXIT_CODE)`)
- Status bar widget shows login state and opens login dialog on click
- SSO/Kerberos login support via GET /UserLogin
- Credentials persisted in IntelliJ PasswordSafe (macOS Keychain)

### Completions & Templates
- CR.* namespace completions (CR.XML, CR.Core, CR.Login, CR.Script, CR.JSON)
- Ext.* completions (Ext.Msg, Ext.Ajax, Ext.each, etc.)
- XML instance method completions (addContainer, addText, addOption, etc.)
- Live templates for common Keyscript patterns
- All completions gated to Keyscript projects only

### Performance
- CIO engine eliminates Netty's 3-5 second startup overhead
- Proxy starts lazily on first login/run/search
- Tool windows use `secondary="true"` to prevent auto-activation
- `buildSearchableOptions` disabled for faster plugin builds
- Metal rendering disabled (`-Dsun.java2d.metal=false`) to fix macOS AWT exceptions

### Bug Fixes
- Fixed blank UI panels caused by `isOpaque = false` in dark theme
- Fixed login dialog not closing on success
- Fixed status bar click not opening login (was passing empty DataContext)
- Fixed script execution 404 errors (proxy not stripping `/Keyscript_IDE/` from paths)
- Fixed duplicate JSESSIONID/instance keys in script parameters
- Fixed IDE freeze during login (EDT blocking from PasswordSafe + listener dispatch)
- Fixed scroll pane sizing (Dimension(0, 120) → proper dimensions)

## [1.0.0] — Initial Prototype

- Basic plugin structure under `com.revfcu.keyscript`
- Keybridge API client
- Simple authentication and settings
- Script options tool window
