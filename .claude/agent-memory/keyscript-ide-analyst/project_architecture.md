---
name: keyscript-ide-architecture
description: Architecture of both the Electron IDE (keyscript-ide-rebuild) and the IntelliJ plugin, covering the proxy pattern, CR framework, ExtJS, and JS bundle structure.
type: project
---

## Two-Codebase System

1. **keyscript-ide-rebuild** (Electron/Express reference implementation)
   - Path: `/Users/msmith/Documents/Development/UI/keyscript-ide-rebuild/`
   - Tech: Node.js + Express + TypeScript + ExtJS 3.2.2 + CodeMirror
   - Entry point: `server/main.ts`

2. **keyscript-intellij-plugin** (IntelliJ IDEA plugin port)
   - Path: `/Users/msmith/Documents/Development/UI/keyscript-intellij-plugin/`
   - Tech: Kotlin + Ktor + IntelliJ Platform SDK + bundled JS assets
   - JS assets: `js-lib/ide-all.js` and `js-lib/keyscript-all.js` (same bundles)

## JS Bundle Architecture

### keyscript-all.js (script runtime, ~25k lines)
- CR.Settings, CR.XML, CR.JSON, CR.Core utilities, all field/container components
- CR.Login (login/session management including Kerberos)
- CR.Script (script-to-host postMessage communication)
- CR.TransactionPanel, CR.Workflow, CR.FMPanel
- CR.HistorySearchPanel, CR.HistoryResultPanel
- CR.KeyStoneService (device service communication)
- **Used in**: both the IDE shell AND script iframes (loaded via head-section.html)

### ide-all.js (IDE shell, ~43.5k lines)
- Everything in keyscript-all.js PLUS:
- CR.DevelopmentScriptsPanel (script tree + parameters form)
- CR.InstalledScriptsPanel (server-side scripts CRUD)
- CR.ScriptManager (URL construction, showScript, createUrl)
- CR.TableBrowser (table schema browser)
- CR.QueryBuilder (visual XML query builder)
- CR.SearchPanel, CR.PersonSearch
- CR.mainPage() (bootstraps the entire IDE UI)
- CodeMirror embedded (line 43447) for source editing
- **Used in**: only the IDE shell (Keyscript_IDE/index.html)

## Proxy Architecture (core innovation)

```
Browser/JCEF iframe
    |
    | HTTP requests (relative URLs like /DirectXMLPostJSON)
    v
Embedded Proxy (Express port 3000 / Ktor)
    |
    | Injects JSESSIONID cookie
    | Rewrites paths (strips /Keyscript_IDE/, prepends /:instance/)
    | Intercepts /SessionStore (param storage) and /UserLogin (cookie fix)
    v
Keystone Server (keystone:8443)
```

## Template Rendering

- `views/keyscript_ide.html` / `public/Keyscript_IDE/index.html` = IDE shell (loads ide-all.js via JSCSSLoader)
- `views/iframe-target.html` = script execution target (loads keyscript-all.js, injects crlogin/crscript params)
- `views/templates/head-section.html` = shared `<head>` with ExtJS + keyscript-all.js refs
- JSCSSLoader = server-side CSS/JS loader from Keystone (provides ExtJS 3.2.2)

## Session Parameter Flow

1. IDE collects: personSerial, accountSerial, workTaskSerial, etc. + full login data
2. POST to `/SessionStore` with `value=<JSON>` — server stores in memory, returns `{id: "..."}`
3. RunScript URL includes `?scriptParametersId=<id>`
4. RunScript handler retrieves params by ID, injects into iframe-target.html as `var scriptParameters = {...}`
5. iframe-target.html injects JSESSIONID cookie and sets `CR.Login.JSESSIONID`

## CR Framework Patterns

- All Keystone API calls use `CR.Core.ajaxRequest({url: 'DirectXMLPostJSON', xmlData: xml.getXMLDocument()})`
- XML queries use `CR.XML` builder: `addContainer`, `addText`, `addOption`, `addCount`, `addSerial`
- Responses parsed via `CR.JSON.parse(response.responseText).query.sequence[].transaction[].step[]`
- Error handling: check `step.tranResult.category.option === 'E'` and `transaction.exception[].message`
- localStorage via `CR.Storage.getItem/setItem` with per-user namespace (`CR.Storage.setUser(userSerial)`)

## Keyscript File Detection (IntelliJ plugin)

Files are Keyscript if:
- Named `*.keyscript.js`, OR
- First 5 lines contain `// @keyscript`

## Script Parameters Structure (crlogin + crscript)

```json
{
  "crlogin": {
    "JSESSIONID": "...",
    "userName": "jsmith",
    "userSerial": "123",
    "deviceName": "WORKSTATION",
    "deviceSerial": "456",
    "locationName": "Test",
    "postingDate": "20240101",
    "sessionID": "...",
    "branchName": "MAIN",
    "branchSerial": "1",
    "databaseName": "TESTDB",
    "instance": "Test",
    "jaspersoftBaseURL": "...",
    "adHocReportingUserName": "...",
    "adHocReportingPassword": "...",
    "institutionLicense": "...",
    "activeDirectoryLogonEnabled": false
  },
  "crscript": {
    "personSerial": "...",
    "accountSerial": "...",
    "workTaskSerial": "...",
    "applicationSerial": "...",
    "collectionItemSerial": "...",
    "scriptDefaultPanelId": "cr-script-xxx",
    "scriptPanelId": "cr-script-xxx",
    "hostPanelId": "cr-host-xxx",
    "forward": "",
    "disputeSerial": "",
    "transactionSerial": "",
    "formTypeSerial": "",
    "formPacketInfo": {}
  }
}
```
