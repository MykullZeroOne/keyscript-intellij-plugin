# Errors Reference

## Contents
- Script Runtime Errors
- Proxy / Network Errors
- Type Definition Errors
- esbuild Bundle Errors
- Debugging Checklist

---

## Script Runtime Errors

### ERROR: "CR is not defined" in iframe

**Cause:** Script executed before `keyscript-all.js` finished loading. This happens when code runs at top-level without `CR.Core.defer()`.

```javascript
// BAD — top-level execution races against script load
CR.Core.ajaxRequest({ url: "DirectXMLPostJSON" });  // ReferenceError: CR is not defined

// GOOD — defer waits for ExtJS + CR framework ready
CR.Core.defer(function() {
    CR.Core.ajaxRequest({ url: "DirectXMLPostJSON" });
});
```

**Where to check:** The `iframe-target.html` template wraps user scripts with `CR.Core.defer()` automatically. If you see this error, the template is likely broken or the user is injecting code outside the template wrapper. See the **jcef** skill for iframe loading details.

---

### ERROR: "Ext is not defined"

**Cause:** Same as above — ExtJS hasn't loaded yet. ExtJS 3.2.2 is loaded synchronously in `head-section.html`, but any code that runs before the `<script>` tag finishes will fail.

```javascript
// BAD
var store = new Ext.data.JsonStore({ /* ... */ });  // too early

// GOOD
CR.Core.defer(function() {
    var store = new Ext.data.JsonStore({ /* ... */ });
});
```

---

### ERROR: AJAX success fires but records array is empty

**Cause:** Keystone returned an application-level error (`errorCode != "0"`). The `success` callback fires for HTTP 200 even when Keystone returns a business logic error.

```javascript
// GOOD — always check errorCode first
success: function(response) {
    const errorCode = CR.XML.getFieldValue(response, "errorCode");
    if (errorCode !== "0") {
        const msg = CR.XML.getFieldValue(response, "errorDescription");
        console.error("Keystone error:", errorCode, msg);
        Ext.Msg.alert("Request Failed", msg);
        return;
    }
    // safe to access response.data.records now
}
```

---

### ERROR: "Cannot read properties of undefined (reading 'records')"

**Cause:** `response.data` is undefined — either the request failed at the network level (proxy down, bad URL) or the response structure is unexpected.

```javascript
// Safe access pattern
const records = response?.data?.records ?? [];
if (records.length === 0) {
    // handle empty result or parse error
}
```

Check the **Diagnostics** panel (Network Monitor tab) to see the raw request/response and confirm the proxy is forwarding correctly. See the **ktor** skill for proxy diagnostics.

---

## Proxy / Network Errors

### ERROR: Script runs but no network requests appear in Diagnostics

**Cause:** The proxy server (`ProxyServerService`) hasn't started. It starts lazily on first `runIde` run trigger.

**Fix:** Use the Run action (play button / gutter icon) rather than opening the preview manually. The run action triggers proxy startup. See the **kotlin** skill for `ProxyServerService` lifecycle.

### ERROR: 404 on `/KeyScript/js/keyscript-all.js`

**Cause:** The Ktor static resource route isn't matching, or `js-lib/keyscript-all.js` wasn't included in the plugin JAR.

**Verify with:**
```bash
# Check the file is in resources
ls -la js-lib/keyscript-all.js

# Check Gradle includes js-lib/ in the JAR
./gradlew jar --info | grep keyscript-all
```

See the **gradle** skill for resource inclusion configuration.

---

## Type Definition Errors

### IntelliJ shows "Unresolved reference: CR" in .keyscript.js files

**Cause:** The `cr-types/` directory isn't registered as a JavaScript library source in IntelliJ.

**Fix:** `CRLibraryProvider` (Kotlin class) registers the directory. If completions are missing:
1. Check `CRLibraryProvider` is registered in `plugin.xml` under `javascript.library.provider`
2. Invalidate caches: **File > Invalidate Caches / Restart**
3. Verify `src/main/resources/cr-types/cr-framework.d.ts` is in the plugin JAR

### WARNING: Type declarations diverge from runtime behavior

`cr-framework.d.ts` is maintained manually — there's no automated sync with `keyscript-all.js`. When the CR framework is updated:

Checklist for keeping types in sync:
- [ ] Identify changed/new methods in the new `keyscript-all.js` by diffing bundles
- [ ] Update method signatures in `cr-framework.d.ts`
- [ ] Add JSDoc for any new parameters
- [ ] Test completions in a `.keyscript.js` file in sandbox IDE (`./gradlew runIde`)
- [ ] Remove deprecated declarations that no longer exist at runtime

---

## esbuild Bundle Errors

### ERROR: React app loads but `main()` doesn't execute

**Cause:** Bundle output format. If `keyscript.bundle.json` uses `"format": "esm"`, the IIFE global isn't created and the entry point function never runs in the plain `<script>` tag context.

```json
// GOOD — creates window-scoped IIFE
{ "format": "iife" }

// BAD — requires <script type="module"> which JCEF template doesn't use
{ "format": "esm" }
// BAD — requires CommonJS require() runtime
{ "format": "cjs" }
```

### ERROR: "React is not defined" in esbuild bundle

**Cause:** Using classic JSX transform with React 17+. Use `"jsx": "automatic"` in `keyscript.bundle.json`:

```json
{
  "jsx": "automatic",
  "target": "es2020"
}
```

`"jsx": "automatic"` uses the new JSX transform that doesn't require `import React from 'react'` in every file.

---

## Debugging Checklist

Copy this checklist when a Keyscript script fails silently:

- [ ] Open **Diagnostics > Network Monitor** — confirm proxy is receiving requests
- [ ] Check **Diagnostics > Console** — look for JS runtime errors from the iframe
- [ ] Verify `CR.Core.defer()` wraps all entry-point code
- [ ] Check `errorCode` in `success` callback — Keystone may be returning a business error
- [ ] Inspect `response?.data` with `console.log(JSON.stringify(response))` before accessing `.records`
- [ ] Confirm proxy port matches `CR.Core.keyStoneWebAppURL` in the rendered template
- [ ] If no requests at all: trigger a Run action to ensure `ProxyServerService` has started
