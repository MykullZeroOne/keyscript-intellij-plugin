# Modules Reference

## Contents
- Module Architecture Overview
- Vendored JS Bundles (js-lib/)
- CR Framework Namespaces
- HTML Templates as Runtime Modules
- Adding a New JavaScript Module

---

## Module Architecture Overview

This project has **no TypeScript/ES module compilation pipeline** in the plugin itself. JavaScript assets are organized into three categories:

| Category | Location | Origin | Update via |
|----------|----------|--------|------------|
| Vendored CR framework | `js-lib/keyscript-all.js` | Keystone server repo | Replace file manually |
| Vendored IDE library | `js-lib/ide-all.js` | Keystone server repo | Replace file manually |
| User scripts | `.keyscript.js` files in user projects | Written by users | Editor |
| Test React app | `src/main/resources/scripts/test-react-app/` | This repo, esbuild | `npx esbuild` |

The Kotlin plugin serves `js-lib/` assets as static resources through the Ktor proxy at `/KeyScript/js/`. See the **ktor** skill for routing details.

---

## Vendored JS Bundles (js-lib/)

`keyscript-all.js` and `ide-all.js` are **pre-built, minified bundles committed to source**. They are NOT produced by any build step in this repo.

### When to update

When the Keystone server team ships a new CR framework version:
1. Obtain the new bundle from the Keystone server repository
2. Replace `js-lib/keyscript-all.js` (and/or `ide-all.js`) directly
3. Update `cr-framework.d.ts` to match any API changes
4. Test by running `./gradlew runIde` and verifying scripts execute

### WARNING: Do not attempt to unbundle or edit these files

`keyscript-all.js` is 31,000+ lines of minified JavaScript. Editing it directly causes:
1. Loss of all changes on next bundle update
2. Minification inconsistencies that break source maps
3. Unpredictable runtime behavior from partial edits

If you need to patch behavior, override at the CR.* namespace level in a separate file loaded after the bundle, or update the source in the Keystone server repository.

---

## CR Framework Namespaces

At runtime inside the JCEF iframe, these globals are available after `keyscript-all.js` loads:

```
CR                         — root namespace
├── XML                    — transaction builder
│   ├── getRootElement()
│   ├── createElement()
│   └── getXMLDocument()
├── Core                   — AJAX, deferred execution
│   ├── ajaxRequest()
│   ├── defer()
│   └── displayExceptions()
├── JSON                   — JSON parse/serialize
├── Login                  — session state
│   ├── userName
│   ├── JSESSIONID
│   └── password
├── Script                 — script context
│   ├── scriptSerial
│   └── scriptDescription
├── Field                  — base field
├── GridPanel              — read-only grid
├── EditorGridPanel        — inline-edit grid
├── FormPanel              — form builder
├── TabPanel               — tab container
└── Panel                  — generic panel

Ext                        — ExtJS 3.2.2 root
├── data.*                 — Store, Record, JsonReader
├── grid.*                 — GridPanel, ColumnModel
├── form.*                 — Field types, FormPanel
└── Msg / MessageBox       — dialogs
```

`head-section.html` loads ExtJS CSS + keyscript-all.js and sets:
```html
CR.Core.extBaseURL = "../ext-3.2.2";
CR.Core.keyStoneWebAppURL = "http://localhost:{{ hostPort }}/{{ instance }}/";
```

These template variables are filled by `PreviewContentService` in Kotlin. See the **kotlin** skill for the service implementation.

---

## HTML Templates as Runtime Modules

The three HTML templates in `src/main/resources/templates/` are the "module system" for the JCEF preview:

```
head-section.html       — loads ExtJS + keyscript-all.js
iframe-target.html      — sandboxed script execution host
Keyscript_IDE/index.html — main IDE interface
```

`iframe-target.html` injects per-script state at render time:

```javascript
// Injected by PreviewContentService (Kotlin) at render time
CR.Login.JSESSIONID = "{{ jsessionid }}";
CR.Login.userName = "{{ username }}";
CR.Script.scriptSerial = "{{ scriptSerial }}";
CR.Script.scriptDescription = "{{ scriptDescription }}";
```

When the user's script calls `CR.Core.ajaxRequest()`, requests go to the proxy's relative URL (e.g., `/Development/DirectXMLPostJSON`), where the Ktor proxy appends the stored JSESSIONID cookie before forwarding to Keystone. See the **ktor** skill for proxy cookie injection details.

---

## Adding a New JavaScript Module

If you need to add a new static JS file to the plugin (e.g., a utility loaded alongside user scripts):

1. Place the file in `src/main/resources/` (it will be included in the plugin JAR)
2. Serve it via the Ktor proxy — add a static route in `ProxyRoutes.kt`:

```kotlin
// In proxy/ProxyRoutes.kt
static("/KeyScript/js") {
    resources("js-lib")
}
// Add your new path:
static("/KeyScript/utils") {
    resources("utils")          // src/main/resources/utils/
}
```

3. Reference in `head-section.html`:

```html
<script src="/KeyScript/utils/my-util.js"></script>
```

4. Add type declarations to `cr-framework.d.ts` if it exposes new globals.

See the **ktor** skill for full proxy routing patterns.
