---
name: keyscript-ide-feature-inventory
description: Complete feature inventory of the original KeyscriptIDE (Electron rebuild + bundled JS), catalogued from source. Canonical reference for porting decisions.
type: project
---

## Source Locations

- Electron server: `/Users/msmith/Documents/Development/UI/keyscript-ide-rebuild/server/main.ts`
- IDE JS bundle: `/Users/msmith/Documents/Development/UI/keyscript-ide-rebuild/public/KeyScript/js/ide-all.js` (~43,500 lines)
- Runtime JS bundle: `/Users/msmith/Documents/Development/UI/keyscript-ide-rebuild/public/KeyScript/js/keyscript-all.js` (~25,000 lines)
- Views/templates: `/Users/msmith/Documents/Development/UI/keyscript-ide-rebuild/views/`
- IntelliJ plugin src: `/Users/msmith/Documents/Development/UI/keyscript-intellij-plugin/src/main/kotlin/com/keyscript/plugin/`

## Key Design Facts

- Frontend UI is built entirely on ExtJS 3.2.2 (Sencha), not React/Vue
- CR namespace wraps all components (CR.Panel, CR.GridPanel, CR.FormPanel, etc.)
- `keyscript-all.js` = script runtime (CR.Script.*, CR.Login.*, base framework)
- `ide-all.js` = IDE shell (Development/Installed panels, TableBrowser, QueryBuilder, main page)
- CodeMirror embedded at ide-all.js line 43447 for source code editing in Installed Scripts panel
- `keyscript.bundle.json` configures esbuild for React/bundled script projects

## Feature Inventory

### 1. Authentication & Session Management

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Login dialog | Username/password form with location name in title | ide-all.js line 20050: `CR.Login.performLogin()` |
| Active Directory / Kerberos login | Optional AD/Kerberos SSO with popup | ide-all.js lines 20228, 20437: `CR.Login.kerberosLogin()` |
| Session ID management | JSESSIONID stored in `CR.Login.JSESSIONID` and `CR.Login.sessionID` | ide-all.js line 20494 |
| Auto-refresh session | `CR.Login.refreshSessionID()` refreshes session token | ide-all.js line 20991 |
| Logoff | `CR.Login.performLogoff()` clears login state and re-shows login | ide-all.js line 20671 |
| Re-login on session expiry | `CR.Login.resetLogin()` handles session reset with re-authentication | ide-all.js line 20735 |
| Login information dialog | Shows device name, user serial, branch info via `CR.showLoginInformation()` | ide-all.js line 43278 |
| Receipt config loading | Loads per-user email receipt config post-login | ide-all.js line 21103 |
| User interface parameters | Loads OFAC check settings, void confirmation, person verification options | ide-all.js line 21130 |
| Terminology localization | Credit union-specific terminology (share/savings, draft/check, etc.) | ide-all.js line 20002 |
| Window title with posting date | Title = "Location - PostingDate - Username" | ide-all.js line 20571 |

### 2. Script Execution & Preview

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| RunScript endpoint | Serves script iframe HTML with injected parameters | server/main.ts line 266; views/iframe-target.html |
| Session parameter store | POST to `/SessionStore` stores `crlogin`/`crscript` JSON, returns ID | server/main.ts lines 148-178 |
| Script iframe rendering | Injects head section + session params into iframe-target.html | views/iframe-target.html; views/templates/head-section.html |
| Script display modes | "Default panel" (D), "Pop-up" (P), "New window" (W) | ide-all.js lines 36238-36248 (option field config) |
| Script parameters form | Person Serial, Account Serial, Application Serial, Work Task Serial, Collection Item Serial, Display Location | ide-all.js lines 36134-36251 |
| Parameter persistence | Each parameter stored in localStorage via `CR.Storage.setItem()` | ide-all.js line 36144 |
| Script tree (Development Scripts) | Async tree loading via `KeyScriptServlet/List` endpoint, folders + .js files | ide-all.js line 36725-36853 |
| Script tree filter | Live search/filter field with 350ms debounce | ide-all.js line 36746 |
| Script tree toolbar | Reload, Expand All, Collapse All, Run, Debug, Upload buttons | ide-all.js lines 36753-36822 |
| Double-click to run | Runs selected script leaf on double-click | ide-all.js line 36322 |
| Run script | Calls `CR.ScriptManager.showScript()` with all parameter values | ide-all.js line 36374 |
| Debug mode | `crPrepareRun(id, path, true)` passes `debug=true` to RunScript URL | ide-all.js line 36340 |
| Live database confirmation | Warning dialog if running against live DB | ide-all.js lines 36388-36401 |
| Script result tab | Script runs inside an iframe tab in center panel, closable | ide-all.js lines 37591-37643 |
| Script messaging (postMessage) | CR.Script uses `postMessage` for host↔frame communication | keyscript-all.js line 24307 |
| Script inter-communication | `CR.Script.messageKeyStone()`, `CR.Script.runScript()`, `CR.Script.closeScript()` | keyscript-all.js lines 24445-24467 |
| FM popup from script | `CR.Script.showFMPopup()` opens form in host window | keyscript-all.js line 24658 |
| Script show work area | `CR.Script.showWorkArea()` triggers transaction/account/person panel in host | keyscript-all.js line 24885 |
| Global error overlay | Catches uncaught JS errors, shows red overlay at bottom of iframe | views/iframe-target.html lines 6-16 |
| Unhandled promise rejection logging | Console.error on unhandled promise rejections in script iframe | views/iframe-target.html lines 17-19 |
| Script error on load failure | Shows error panel if script file fails to load | views/iframe-target.html lines 39-44 |
| Form packet info | Pre-loads loan request / work task data before running script | ide-all.js line 37827: `CR.ScriptManager.getFormPacketInfo()` |

### 3. Installed Scripts Panel (Server-Side Script Management)

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Installed scripts grid | Grid of all SCRIPT table entries with search and sort | ide-all.js line 37243: `CR.InstalledScriptsPanel.InstalledScriptsGrid` |
| Open script record | Loads full SCRIPT record with all metadata fields | ide-all.js line 36936: `crOpenScript()` |
| Source code editor | CodeMirror editor with line numbers, bracket matching, word wrap | ide-all.js lines 37091-37098 |
| Insert script | Creates new SCRIPT record in Keystone DB | ide-all.js line 37022: toolbar Insert button |
| Update script | Updates existing SCRIPT record SOURCE_CODE and metadata | ide-all.js line 37021: toolbar Update button |
| Delete script | Deletes SCRIPT record | ide-all.js line 37023: toolbar Delete button |
| Run installed script | Runs a server-side script directly by serial | ide-all.js line 37024: toolbar Run button |
| Download script | Downloads script source via `LoadScript?scriptSerial=&attachment=true` | ide-all.js lines 36981-36985 |
| Upload script (from Dev panel) | Searches SCRIPT table by filename, inserts or updates record | ide-all.js lines 36404-36702: `crUploadScript()` |
| Upload confirmation window | Dialog to select existing script serial or insert new | ide-all.js line 36479: `crUploadScriptWindow()` |
| Script other info panel | East panel with non-source-code fields (description, type, work area, etc.) | ide-all.js lines 37072-37080 |

### 4. Data Tools — Table Browser

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Table tree navigation | Tree panel listing all Keystone tables | ide-all.js line 38491: `CR.TableBrowser` |
| Draggable tabs | Table detail tabs support drag-and-drop reordering via `Ext.ux.DraggableTabs` | ide-all.js line 38527 |
| Table columns display | Shows all column metadata for a table (name, type, description) | ide-all.js line 41112: `CR.TableBrowser.showTableColumns()` |
| Column filter | Filter field to narrow visible columns | ide-all.js: `columnFilterRegex` |
| Developer security check | Verifies user has "Developer" security event access before showing advanced features | ide-all.js line 41384: `CR.TableBrowser.developerSecurityCheck()` |
| Print single table | Generates printable HTML of table schema | ide-all.js line 41116: `CR.TableBrowser.crGetPrintHtml()` |
| Print all open tables | Opens a new window with all currently open table schemas | ide-all.js line 41524: `CR.TableBrowser.printAllTableContents()` |
| Print table context menu | Right-click menu on tabs: Close, Close Other, Close All, Print Table, Print All | ide-all.js lines 38529-38599 |
| Inline table help | Collapsible east panel showing Confluence documentation iframe | ide-all.js lines 38508-38523 |
| Copy button | Copies table schema content to clipboard | ide-all.js line 41771: `CR.TableBrowser.addCopyButton()` |
| TableBrowser in main toolbar | Direct access from top toolbar button | ide-all.js line 43223 |
| TableBrowser in Options menu | Also accessible via Options > Table Browser | ide-all.js line 43183 |

### 5. Data Tools — Query Builder

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Query Builder panel | Multi-panel tool for constructing XML queries visually | ide-all.js line 41822: `CR.QueryBuilder` |
| Element panel (west) | Tree of available XML elements/containers to add | ide-all.js line 41829: `CR.QueryBuilder.ElementPanel` |
| Query panel (center) | Visual tree of the current query being built | ide-all.js line 41835: `CR.QueryBuilder.QueryPanel` |
| Property panel (east) | Grid showing properties of the selected query node | ide-all.js line 41839: `CR.QueryBuilder.PropertyPanel` |
| Help panel (south) | Contextual documentation for selected property | ide-all.js lines 41900-41912 |
| Double-click to add element | Double-clicking an element type adds it as child of selected node | ide-all.js lines 41860-41884 |
| XML generation | `CR.QueryBuilder.getXML()` serializes visual tree to CR.XML document | ide-all.js line 41931 |
| Manual edit mode | `CR.QueryBuilder.manualEdit()` allows direct XML text editing | ide-all.js line 42741 |
| Property type handling | Handles Option, Date, Money, Count data types in properties | ide-all.js lines 41951-41969 |
| Query Builder in main toolbar | Direct access from top toolbar | ide-all.js line 43230 |

### 6. Data Tools — Search

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Search panel | `CR.SearchPanel` generic search with grid results | ide-all.js line 21262 |
| Search window | Standalone popup window for search | ide-all.js line 21896: `CR.SearchPanel.openWindow()` |
| Insert button config | Optional insert button in search panel | ide-all.js line 22060: `CR.SearchPanel.createInsertButtonConfig()` |
| Reversible list toggle | Toggle between normal and reversible search results | ide-all.js line 22274: `CR.SearchPanel.toggleReversibleList()` |
| Person search | Extended person search panel with account association | ide-all.js line 22501: `CR.PersonSearch` |
| Serial detail inquiry | View record from search result row | ide-all.js line 22341: `CR.SearchPanel.serialDetailInquiry()` |
| Transaction history | View transaction history from search results | ide-all.js line 22379: `CR.SearchPanel.displayTransactionHistory()` |
| Check history | View check history from search results | ide-all.js line 22448: `CR.SearchPanel.displayCheckHistory()` |

### 7. UI Main Page Layout

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Main page function | `CR.mainPage()` bootstraps the entire IDE UI | ide-all.js line 43006 |
| Two-tab main layout | "Development Scripts" and "Installed Scripts" tabs | ide-all.js lines 43012-43015 |
| Main toolbar | "KeyScript IDE" title, Table Browser, Query Builder, Options menu | ide-all.js lines 43237-43245 |
| Location indicator | Red location name shown in non-live databases | ide-all.js lines 43246-43253 |
| Options menu | Dropdown: Theme, Login Information, Logoff, Help, ExtJS Docs, ExtJS Examples, About | ide-all.js line 43217 |
| Context menu on main tabs | Close Workarea, Close All Workareas, Close All Other Workareas | ide-all.js lines 43017-43055 |
| Clear Tabs button | Clears all open workareas with confirmation | ide-all.js line 43057 |
| Context menu on dev panel tabs | Close Tab, Close Other Tabs, Close All Tabs | ide-all.js lines 36276-36315 |

### 8. Theming

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Theme switcher | Menu with Blue, Gray, Dark (beta), Cobalt (beta), Slate themes | ide-all.js lines 43102-43134 |
| Theme persistence | Theme saved to `CR.Settings` under key `Corelation_Theme` | ide-all.js line 43100 |
| DarkStone CSS | Custom dark theme stylesheet | public/Keyscript_IDE/css/DarkStone.css |
| DarkStoneCobalt CSS | Custom cobalt/blue-dark theme | public/Keyscript_IDE/css/DarkStoneCobalt.css |
| Dynamic theme swapping | `CR.Core.setTheme()` uses `Ext.util.CSS.swapStyleSheet()` | ide-all.js line 11409 |

### 9. Settings (User Preferences)

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Settings persistence (localStorage) | `CR.Settings.set/get/remove` with 30s auto-save | ide-all.js line 1 |
| Settings persistence (server) | Save user settings to DB every 60 minutes | ide-all.js lines 24-36 |
| Settings dirty flag | Tracks local and server dirty states separately | ide-all.js lines 5-6 |
| Recent rows tracking | Per-table recent record MRU list | ide-all.js line 777: `CR.Settings.getRecentRows()` |
| Bulk settings save | `CR.Settings.saveCurrentUserSettings()` with localStorage + server sync | ide-all.js line 412 |
| Settings unload save | Saves dirty settings on page unload | ide-all.js line 28 |

### 10. CR Framework Field Components (keyscript-all.js)

| Component | Description | Source Location |
|-----------|-------------|-----------------|
| `CR.TextField` | Text input with CR column binding | keyscript-all.js line 2991 |
| `CR.TextAreaField` | Multi-line text with newline support | keyscript-all.js line 3429 |
| `CR.MoneyField` | Currency input with display conversion | keyscript-all.js line 3566 |
| `CR.RateField` | Interest rate input with conversion | keyscript-all.js line 3784 |
| `CR.DateField` | Date picker with MMDDYY parsing | keyscript-all.js line 4122 |
| `CR.TimeField` | Time picker with timezone lookup table | keyscript-all.js line 4357 |
| `CR.CountField` | Numeric count field | keyscript-all.js line 4003 |
| `CR.OptionField` | Dropdown/ComboBox for option types | keyscript-all.js line 1821 |
| `CR.SerialField` | FK-style serial lookup ComboBox | keyscript-all.js line 2259 |
| `CR.Checkbox` | Checkbox with CR binding | keyscript-all.js line 4837 |
| `CR.ColorPickerField` | Color picker with foreground contrast calculation | keyscript-all.js line 3110 |
| `CR.CardNumberField` | Credit/debit card number input with trigger | ide-all.js line 3806 |
| `CR.DocumentField` | Multi-line text allowing newlines (DocumentField) | keyscript-all.js (extends CR.TextAreaField) |
| `CR.BinaryField` | File upload/download/clear for binary column data | ide-all.js line 6957 |

### 11. CR Framework Container Components

| Component | Description | Source Location |
|-----------|-------------|-----------------|
| `CR.Panel` | Base panel with print support | keyscript-all.js line 5964 |
| `CR.FormPanel` | Form layout panel | keyscript-all.js line 6774 |
| `CR.GridPanel` | Data grid extending Ext.grid.GridPanel | keyscript-all.js line 6811 |
| `CR.EditorGridPanel` | Inline-editable grid | ide-all.js line 7751 |
| `CR.TabPanel` | Tabbed panel with scroll support | keyscript-all.js line 5935 |
| `CR.TreePanel` | Tree with node animation | keyscript-all.js (extends Ext.tree.TreePanel) |
| `CR.ListView` | List view component | ide-all.js line 7721 |
| `CR.FieldSet` | Field grouping container | keyscript-all.js line 5771 |
| `CR.Window` | Popup window component | keyscript-all.js line 5831 |
| `CR.ToolTip` | Tooltip component | keyscript-all.js line 5878 |
| `CR.Tip` | Simpler tip component | ide-all.js line 7599 |
| `CR.Button` | Button with CR styling | keyscript-all.js line 5297 |
| `CR.ToolbarButton` | Toolbar-specific button with menu support | keyscript-all.js line 5335 |
| `CR.Menu` / `CR.MenuItem` | Context/dropdown menus | ide-all.js lines 8170-8201 |
| `CR.GridCheckColumn` | Checkbox column plugin for grids | ide-all.js line 8043 |

### 12. CR Framework Business Panels

| Component | Description | Source Location |
|-----------|-------------|-----------------|
| `CR.FMPanel` | File maintenance panel for CRUD on Keystone records | ide-all.js line 12112 |
| `CR.SearchPanel` | Generic search panel with grid results | ide-all.js line 21262 |
| `CR.PersonSearch` | Person-specific search with household/associations | ide-all.js line 22501 |
| `CR.RecordReferencePanel` | Grid showing record references | ide-all.js line 23268 |
| `CR.SecurityOverridePanel` | Security override prompt panel | ide-all.js line 23517 |
| `CR.TreeViewPanel` | Hierarchical record tree viewer | ide-all.js line 24175 |
| `CR.SearchAndTreeViewPanel` | Combined search + tree panel | ide-all.js line 26402 |
| `CR.HistorySearchPanel` | Transaction history search | ide-all.js line 15686 |
| `CR.HistoryResultPanel` | Transaction history display grid | ide-all.js line 17628 |
| `CR.TransactionPanel` | Full teller transaction panel | ide-all.js line 28024 |
| `CR.Workflow` | Workflow/application processing panel | ide-all.js line 31208 |
| `CR.PDFDocument` | PDF form rendering panel | ide-all.js line 26709 |

### 13. Server-Side API Proxy (Express/main.ts)

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| HTTP proxy to Keystone | All POST requests proxied to `proxyEndpoint` (default: keystone:8443) | server/main.ts line 134 |
| HTTPS support | `NODE_TLS_REJECT_UNAUTHORIZED=0` or SSL cert files | server/main.ts lines 182-186 |
| Instance routing | `/Test/`, `/Development/`, etc. routes set `KSInstance` variable | server/main.ts lines 194-199 |
| Keyscript_IDE path rewriting | Strips `/Keyscript_IDE/` from POST paths before proxying | server/main.ts lines 118-121 |
| Keybridge endpoint routing | Known endpoints (/DirectXMLPostJSON, /UserLogin, etc.) get instance prepended | server/main.ts line 139 |
| Session store intercept | POST to `/SessionStore` captured, params stored in memory by seq ID | server/main.ts lines 148-163 |
| UserLogin cookie fix | Broadens JSESSIONID cookie Path from `/Instance` to `/` | server/main.ts lines 168-178 |
| Request/response recording | `req.session.recording` flag enables console logging of all traffic | server/main.ts lines 143-146 |
| Script tree endpoint | `KeyScriptServlet/List` returns folder/file tree of `public/scripts/` | server/main.ts lines 122-133 |
| Static file serving | Falls back to public/ directory for static assets | server/main.ts lines 312-320 |
| TypeScript source serving | `.ts` files with `sec-fetch-dest: empty` served from parent directory | server/main.ts lines 305-310 |
| Protocol support | HTTP or HTTPS based on `HTTPS` env var | server/main.ts lines 363-384 |
| Sprightly template engine | Uses sprightly for HTML template variable substitution | server/main.ts lines 42-52 |

### 14. Device Service (CorelationWindowsService mock)

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| GetDeviceInformation | Returns XML device info with MAC address and service port | server/main.ts lines 346-359 |
| Service port | Runs on `hostPort + 1` (default: 3001) | server/main.ts line 26 |
| CORS restriction | Only accepts requests from main app origin | server/main.ts lines 341-344 |
| CR.KeyStoneService | Client-side service communication layer for printers, scanners, forms | ide-all.js line 19017 |
| Device printer support | Receipt, endorsement, check, card, cam printers via service | ide-all.js lines 11042-11190 |
| Interaction channel | Device interaction channel for service integration | ide-all.js line 11179 |

### 15. CR Core Utilities

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| `CR.Core.ajaxRequest()` | Central AJAX with XML data, override/mask/retry support | ide-all.js line 8657 |
| `CR.Core.ajaxOverrideHeaderHandler()` | Intercepts AJAX responses to handle exceptions/overrides | ide-all.js line 8781 |
| `CR.Core.ajaxExceptionHeaderHandler()` | Handles server-side exception responses | ide-all.js line 9471 |
| `CR.Core.recordView()` | Helper to view a single DB record | ide-all.js line 9602 |
| `CR.Core.recordUpdate()` | Helper to update a DB record | ide-all.js line 9742 |
| `CR.Core.record()` | Generic record operation (V/I/U/D) | ide-all.js line 9850 |
| `CR.Core.recordSearch()` | Helper to search the DB | ide-all.js line 10011 |
| `CR.Core.recordTree()` | Helper to load record tree | ide-all.js line 10087 |
| `CR.Core.displayExceptions()` | Displays error dialog with exception list | ide-all.js line 10237 |
| `CR.Core.confirm()` | Confirm dialog wrapper | ide-all.js line 10366 |
| `CR.Core.searchPrompt()` | Search prompt dialog | ide-all.js line 10394 |
| `CR.Core.getJSessionID()` | Retrieves current JSESSIONID | ide-all.js line 10836 |
| `CR.Core.base64Encode()` | Base64 encoding utility | ide-all.js line 10921 |
| `CR.Core.getDevicePrinters()` | Fetches device printer configuration | ide-all.js line 11042 |
| `CR.Core.sendEmailNotification()` | Sends email via Keystone email system | ide-all.js line 11458 |
| `CR.Core.isLiveDatabase()` | Checks if connected to live instance (DB name ends in "LIV") | ide-all.js line 11455 |
| `CR.Core.includeJSCSS()` | Dynamically loads external JS/CSS files | ide-all.js line 2071 |
| `CR.Core.setTheme()` | Swaps ExtJS theme stylesheet | ide-all.js line 11409 |
| `CR.Core.getPostingDate()` | Gets current posting date from server | keyscript-all.js line 9830 |
| `CR.XML` | XML document builder for Keystone queries | ide-all.js line 875 |
| `CR.JSON` | JSON parse/stringify wrapper | ide-all.js line 2151 |
| `CR.Storage` | localStorage wrapper with per-user namespacing | ide-all.js line 2177 |
| `CR.SerialObject` | Serial FK resolution helper | ide-all.js line 2796 |

### 16. Recording / Network Debug

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Request recording toggle | `/api/record` and `/api/stop` endpoints toggle console logging of all proxied traffic | server/main.ts lines 99-108 |
| Recording status | `/api/status` returns current recording state | server/main.ts lines 95-98 |
| Session-based recording flag | `req.session.recording` flag persists across requests | server/main.ts line 15-18 |

### 17. Multi-Instance Support

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Supported instances config | `SUPPORTED_INSTANCES` env var (pipe-delimited, default: "Test") | server/main.ts line 30 |
| Instance routing | Routes /:instance/* automatically set `KSInstance` | server/main.ts lines 194-199 |
| Redirect to Keyscript_IDE | /:instance/ redirects to /:instance/Keyscript_IDE/ | server/main.ts lines 323-326 |
| Root redirect | `/` redirects to `/Test/Keyscript_IDE/` | server/main.ts line 328 |
| Live instance block | `/Live/` routes return 403 | server/main.ts lines 288-290 |

### 18. Script Bundling (esbuild integration — IntelliJ plugin)

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| keyscript.bundle.json | Config file: entry, outfile, format, target, minify, jsx, external, define, inject, loader | BundleService.kt lines 32-43 |
| Bundle command | Calls esbuild with all config flags | BundleService.kt lines 125-163 |
| esbuild auto-discovery | Checks project-local node_modules/.bin/esbuild, then PATH | BundleService.kt lines 82-101 |
| npm init + install | Can auto-install esbuild via npm | BundleService.kt lines 217-256 |
| Bundle to string | Bundles and returns output as string (for deployment) | BundleService.kt line 203 |
| Watch mode | `--watch` flag support | BundleService.kt line 163 |
| JSX support | automatic or transform JSX modes | BundleService.kt lines 133-141 |
| Test React app | Sample project at public/scripts/test-react-app/ with keyscript.bundle.json | keyscript.bundle.json |

### 19. Deployment (IntelliJ plugin)

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| Search by description | Search SCRIPT table by description via DirectXMLPostJSON | DeploymentService.kt line 37 |
| Deploy new script | INSERT to SCRIPT table with DESCRIPTION, LANGUAGE=JS, CATEGORY=C, SOURCE_CODE, CLIENT_TRAN_WORK_AREA_OPTION | DeploymentService.kt line 60 |
| Deploy update | UPDATE existing SCRIPT record by serial with SOURCE_CODE (and optionally DESCRIPTION, workAreaOption) | DeploymentService.kt line 93 |
| Session expiry detection | 401/403 or "session expired" in response body triggers re-login | DeploymentService.kt line 338 |
| Work area option | CLIENT_TRAN_WORK_AREA_OPTION field set on deploy (D = default, P = popup, W = new window) | DeploymentService.kt lines 63, 77 |

### 20. Code Completions (IntelliJ plugin)

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| CR.* namespace completions | XML, JSON, Core, Login, Script, Panel, Settings, Storage, KeyStoneService, GridPanel, EditorGridPanel, FormPanel, TabPanel, TextField, DateField, MoneyField | CRCompletionContributor.kt lines 52-75 |
| CR.Core.* completions | ajaxRequest, displayExceptions, defer, viewPort, keyStoneWebAppURL, findFields | CRCompletionContributor.kt lines 77-100 |
| CR.Login.* completions | userName, userSerial, sessionID, JSESSIONID, postingDate, locationName, databaseName, instance | CRCompletionContributor.kt lines 102-117 |
| CR.Script.* completions | personSerial, accountSerial, scriptDefaultPanelId, scriptDescription, runScript, runForm, includeJSCSS | CRCompletionContributor.kt lines 119-142 |
| CR.JSON.* completions | parse, stringify | CRCompletionContributor.kt lines 144-153 |
| Ext.* completions | Msg, Viewport, Panel, Ajax, each, apply, getCmp, onReady | CRCompletionContributor.kt lines 155-180 |
| Ext.Msg.* completions | alert, confirm, prompt, show | CRCompletionContributor.kt lines 182-193 |
| CR.XML instance completions | getRootElement, getXMLDocument, addContainer, addText, addOption, addCount, addMoney, addDate, addRate | CRCompletionContributor.kt lines 195-211 |
| Project-scoped activation | Completions only activate in Keyscript projects | CRCompletionContributor.kt line 31 |

### 21. Live Templates (IntelliJ plugin)

| Template | Description |
|----------|-------------|
| `cr-xml-transaction` | Single CR.XML transaction with record |
| `cr-xml-multi-step` | Multi-step transaction template |
| `cr-xml-field` | Add field to XML record |
| `cr-ajax-request` | CR.Core.ajaxRequest call skeleton |
| `cr-ajax-promise` | Promise-wrapped ajaxRequest |
| `cr-ajax-function` | Complete async data fetch function |
| `cr-parse-response` | Parse DirectXMLPostJSON response |
| `cr-get-field` | Get field value from record |
| `cr-grid-panel` | CR.GridPanel with store and columns |
| `cr-editor-grid` | CR.EditorGridPanel with inline editing |
| `cr-form-panel` | CR.FormPanel with CR fields |
| `cr-tab-panel` | CR.TabPanel with tabs |
| `cr-viewport` | CR.Panel + Ext.Viewport setup |
| `cr-defer` | CR.Core.defer block |
| `cr-display-exceptions` | Display exceptions if any |
| `cr-search-json` | SearchJSON request template |
| Context: KEYSCRIPT_JS | All templates scoped to `KEYSCRIPT_JS` context |

### 22. File Detection & Project Support

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| .keyscript.js suffix | Files named `*.keyscript.js` are always Keyscript files | KeyscryptFileSupport.kt line 9 |
| `// @keyscript` marker | Any `.js` file with marker in first 5 lines is a Keyscript file | KeyscryptFileSupport.kt line 22 |
| 2KB prefix scan | Only reads first 2048 bytes of file for efficiency | KeyscryptFileSupport.kt line 10 |
| keyscript.bundle.json | Presence in project root triggers bundling features | BundleService.kt line 71 |

### 23. Session Service (IntelliJ plugin)

| Feature | Description | Source Location |
|---------|-------------|-----------------|
| JSESSIONID management | Stores proxy session ID for iframe/preview requests | SessionService.kt line 49 |
| Keystone API session ID | Separate session for direct API calls (deploy/search) | SessionService.kt line 52 |
| Heartbeat timer | 45-second periodic keepalive pings to both proxy and API sessions | SessionService.kt line 374 |
| Proxy keepalive | GET to `/:instance/UserLogin` with JSESSIONID cookie | SessionService.kt line 225 |
| API keepalive | POST lightweight search query to Keystone API | SessionService.kt line 284 |
| Consecutive failure detection | 2 consecutive failures before declaring session expired | SessionService.kt line 377 |
| Auto-relogin | Re-logins automatically if saved credentials exist | SessionService.kt line 100 |
| Credential persistence | IntelliJ PasswordSafe (OS-integrated, encrypted) | SessionService.kt lines 334-350 |
| Listener pattern | CopyOnWriteArrayList of listeners notified on state change | SessionService.kt line 37 |
| Login data storage | Full login map (postingDate, locationName, databaseName, etc.) | SessionService.kt line 57 |

### 24. Tool Windows (IntelliJ plugin)

| Tool Window | Content | Factory |
|------------|---------|---------|
| Workspace (right) | Script Options (person/account params) + Session tab | KeyscryptWorkspaceToolWindowFactory.kt |
| Data Tools - Table Browser | Table list, Columns, Search Records, Record View, Record Operations | TableBrowserToolWindowFactory.kt |
| Data Tools - Query Builder | Visual query builder panel | QueryBuilderToolWindowFactory.kt |
| Data Tools - Search | Search panel | SearchToolWindowFactory.kt |
| Diagnostics - Console | Script console output | ConsoleToolWindowFactory.kt |
| Diagnostics - Network | HTTP request/response monitor | NetworkToolWindowFactory.kt |

### 25. Table Browser (IntelliJ plugin — enhanced)

| Feature | Description |
|---------|-------------|
| Table list with filter | Sidebar list of all Keystone tables, live filter by name/description |
| Columns tab | Full column metadata: ordinal, name, description, type, null, maxLength, default, references |
| Search Records tab | Filter combo, param field, search button, results grid with double-click to view record |
| Filter parameter detail | Shows column names and data types for selected filter |
| Search template (XML/JS toggle) | Generates XML or JS code template for selected filter |
| Copy template button | Copies template to clipboard |
| Record View tab | Field/value pairs for a specific record |
| Record Operations tab | V/I/U/D toggle generates XML + JS templates with all fields |
| Copy XML / Copy JS buttons | Copies generated templates to clipboard |
| Lazy loading | Table list loaded on first component show |
| Parent table name | Detected for I operations (targetParentSerial) |

### 26. Proxy Server (IntelliJ plugin)

| Route | Description | Source |
|-------|-------------|--------|
| POST /api/sso-session | Store JSESSIONID from login | ProxyRoutes.kt line 102 |
| POST /api/device-id | Store device identifier | ProxyRoutes.kt line 117 |
| POST /api/set-project | Set active project path | ProxyRoutes.kt line 132 |
| GET /api/get-project | Get active project path | ProxyRoutes.kt line 140 |
| GET /project-scripts/* | Serve script files from project directory (with override support) | ProxyRoutes.kt line 151 |
| GET /GetDeviceInformation | Returns XML device info with MAC addresses | ProxyRoutes.kt line 175 |
| POST /DirectXMLPostJSON | Proxies to Keystone with JSESSIONID cookie | ProxyRoutes.kt line 196 |
| POST /SearchJSON | Proxies search requests to Keystone | ProxyRoutes.kt line 228 |
| GET /UserLogin | Proxies UserLogin to Keystone | ProxyRoutes.kt line 260 |
| GET /{inst}/Keyscript_IDE/RunScript | Renders script iframe HTML with injected params | ProxyRoutes.kt line 270 |
| POST {path...} | Catch-all POST proxy with SessionStore intercept, JSESSIONID injection | ProxyRoutes.kt line 323 |
| GET {path...} | Catch-all GET proxy with bundled static asset serving | ProxyRoutes.kt line 402 |
| Bundled static serving | Serves js-lib assets (KeyScript/, Keyscript_IDE/, scripts/) from classpath | ProxyRoutes.kt lines 459-500 |
| Preview script override | In-memory map of scriptPath -> content for live preview | ProxyRoutes.kt line 159 |
| Network event capture | All requests/responses logged to NetworkMonitorService | ProxyRoutes.kt lines 210-216, 348-350, 389-391 |
