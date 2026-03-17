# Patterns Reference

## Contents
- Script Execution Flow
- CR Framework Patterns
- Error Handling
- Live Template Patterns
- esbuild Bundle Patterns

---

## Script Execution Flow

Every Keyscript user script runs inside `iframe-target.html`, which:
1. Loads ExtJS 3.2.2 + keyscript-all.js from the Ktor proxy
2. Sets CR.Login.JSESSIONID from injected script parameters
3. Wraps the user script in `CR.Core.defer()` to wait for Ext.onReady

Always wrap async entry points in `CR.Core.defer()` — **never** use `Ext.onReady` directly:

```javascript
// GOOD — CR.Core.defer ensures CR.* namespaces are ready
CR.Core.defer(async function main() {
    // safe to use CR.*, Ext.*, etc.
});

// BAD — Ext.onReady fires before CR.* initialization is complete
Ext.onReady(function() {
    CR.Core.ajaxRequest(/* ... */);  // CR.Core may not be ready
});
```

---

## CR Framework Patterns

### Building XML transactions

The `CR.XML` builder constructs Keystone DirectXMLPostJSON payloads. Chain `.addContainer()` to build the hierarchy, use `.setAttribute()` on containers:

```javascript
const xml = new CR.XML();
xml.getRootElement()
    .addContainer("sequence")
    .addContainer("transaction").setAttribute("name", "MY_TXN")
    .addContainer("step")
    .addContainer("record")
    .addText("tableName", "EMPLOYEE")
    .addText("filter", "status='ACTIVE'");

CR.Core.ajaxRequest({
    url: "DirectXMLPostJSON",
    xmlData: xml.getXMLDocument(),
    success: handleResponse,
    failure: handleError
});
```

### SearchJSON requests (preferred for simple queries)

```javascript
CR.Core.ajaxRequest({
    url: "SearchJSON",
    params: {
        table: "EMPLOYEE",
        fields: "empId,firstName,lastName",
        filter: "status='ACTIVE'",
        limit: 100
    },
    success: function(response) {
        const records = response.data?.results ?? [];
    }
});
```

### WARNING: Missing error check on ajaxRequest response

**The Problem:**

```javascript
// BAD — errorCode 0 means success; non-zero means failure
success: function(response) {
    const records = response.data.records;  // crashes if errorCode != 0
    records.forEach(/* ... */);
}
```

**Why This Breaks:**
1. Keystone returns HTTP 200 even for application-level errors (wrong credentials, bad filter)
2. `response.data.records` is undefined when errorCode != "0"
3. Silent crashes with no user feedback

**The Fix:**

```javascript
success: function(response) {
    const errorCode = CR.XML.getFieldValue(response, "errorCode");
    if (errorCode !== "0") {
        Ext.Msg.alert("Error", CR.XML.getFieldValue(response, "errorDescription"));
        return;
    }
    const records = response.data?.records ?? [];
    records.forEach(/* ... */);
}
```

---

## Error Handling

The `iframe-target.html` template installs a global `window.onerror` handler that renders errors visually in the iframe. Still handle errors explicitly for user-facing feedback:

```javascript
CR.Core.defer(async function main() {
    try {
        await runMyLogic();
    } catch (err) {
        // Surfaces in JCEF preview and Diagnostics console
        CR.Core.displayExceptions([{
            message: err.message ?? String(err),
            stack: err.stack
        }]);
    }
});
```

---

## Live Template Patterns

Live templates in `src/main/resources/liveTemplates/Keyscript.xml` are scoped to `KEYSCRIPT_JS` context. When adding a template, follow the existing pattern — use `$VAR$` for cursor positions, `$END$` for final cursor:

```xml
<template name="cr-my-template" value="CR.Core.ajaxRequest({&#10;    url: &quot;$URL$&quot;,&#10;    success: function(response) {&#10;        $END$&#10;    }&#10;});" description="My template description" toReformat="true" toShortenFQNames="true">
    <variable name="URL" expression="" defaultValue="&quot;DirectXMLPostJSON&quot;" alwaysStopAt="true" />
    <context>
        <option name="KEYSCRIPT_JS" value="true" />
    </context>
</template>
```

Key rules:
- Always set `toReformat="true"` — templates insert without formatting otherwise
- Scope ONLY to `KEYSCRIPT_JS` — these templates are irrelevant in non-Keyscript files
- Newlines in `value` attribute must be `&#10;`, quotes must be `&quot;`

---

## esbuild Bundle Patterns

For modern JS/React Keyscript apps, `keyscript.bundle.json` controls esbuild:

```json
{
  "entry": "src/index.jsx",
  "outfile": "dist/bundle.js",
  "format": "iife",
  "target": "es2020",
  "minify": false,
  "jsx": "automatic"
}
```

IIFE format is required — the bundle runs in a plain `<script>` tag in the JCEF iframe with no module loader. `jsx: "automatic"` uses the React 18 transform (no need to import React in each file).

### WARNING: Using `"format": "esm"` in bundle config

ESM bundles require `<script type="module">` and a module-aware loader. The JCEF iframe template uses a plain `<script src="...">` tag. ESM bundles will silently fail to execute — exports are not attached to `window` and `main()` never runs.

See the **jcef** skill for iframe template details.
