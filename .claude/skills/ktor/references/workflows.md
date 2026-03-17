# Ktor Workflows Reference

## Contents
- Adding a new proxy route
- Debugging proxy startup failures
- Authentication flow walkthrough
- Modifying network monitoring
- Build and test cycle

---

## Adding a New Proxy Route

Copy this checklist and track progress:

- [ ] 1. Define route as a private `Route.` extension in `ProxyRoutes.kt`
- [ ] 2. Register in `configure()` **before** the two catch-all routes
- [ ] 3. Inject JSESSIONID via `CookieInjector.injectCookie()`
- [ ] 4. Emit paired request/response events to `networkMonitor`
- [ ] 5. Pass through upstream `Content-Type` in the response
- [ ] 6. `./gradlew build` — fix compile errors
- [ ] 7. `./gradlew runIde` — manually verify in Diagnostics > Network Monitor

**Minimal route template:**

```kotlin
private fun Route.postMyNewRoute() {
    post("/api/my-route") {
        val body = call.receiveText()
        val targetUrl = "$proxyUrl/$currentInstance/MyRoute"
        val requestId = System.nanoTime().toString(36)

        networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
            id = requestId, type = "request", method = "POST",
            url = targetUrl, body = body.take(500)
        ))

        val response = httpClient.post(targetUrl) {
            contentType(ContentType.parse(call.request.contentType().toString()))
            header("Cookie", CookieInjector.injectCookie(
                call.request.headers["Cookie"], proxyService.ssoSessionId
            ))
            setBody(body)
        }

        val responseBody = response.bodyAsText()
        networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
            id = requestId, type = "response",
            status = response.status.value, body = responseBody.take(500)
        ))

        call.respondText(
            responseBody,
            ContentType.parse(response.headers[HttpHeaders.ContentType] ?: "application/json"),
            response.status
        )
    }
}
```

Register in `configure()`:
```kotlin
fun configure(app: Application) {
    app.routing {
        // ... existing specific routes
        postMyNewRoute()                                  // before catch-alls
        post("{path...}") { catchAllPost(call) }
        get("{path...}") { catchAllGet(call) }
    }
}
```

---

## Debugging Proxy Startup Failures

The proxy starts lazily via `ProxyServerService.ensureStarted()`. Failures are logged but don't crash the IDE — check IntelliJ's "Run" panel for output.

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Address already in use` | Port 3000 taken | Change `proxyPort` in Settings > Keyscript IDE or kill the process |
| Preview blank, no network events | Proxy never started | Trigger login or Run to force `ensureStarted()` |
| All routes return 404 | Catch-all fires before specific route | Move specific route above `{path...}` in `configure()` |
| SSL handshake error | TrustManager not applied | Confirm `expectSuccess = false` and `trustManager` inside `engine {}` block |
| Script runs but no JSESSIONID | `ssoSessionId` empty at request time | Verify login flow sets `proxyService.setSsoSession()` before RunScript |

**Verify proxy is running:**

```kotlin
// getProxyBaseUrl() calls ensureStarted() internally
val url = ProxyServerService.getInstance(project).getProxyBaseUrl()
// Returns "http://localhost:3000" (or configured proxyPort)
```

Iterate-until-pass:
1. Change proxy code
2. `./gradlew build` — verify no compile errors
3. `./gradlew runIde`
4. Trigger proxy start (login or run a script)
5. Check Run panel for `"Proxy server started on port ..."` — if missing, look for exceptions before it
6. Fix root cause and repeat from step 1

---

## Authentication Flow Walkthrough

Required reading before modifying login, SSO, or session injection.

```
User clicks Login
  → AuthenticationService.login()
      → ProxyServerService.ensureStarted()          // proxy must be up first
      → GET http://localhost:3000/{instance}         // sets currentInstance on ProxyRoutes
      → POST http://localhost:3000/api/device-id     // registers device MAC
      → POST http://localhost:3000/UserLogin         // goes through catchAllPost
          → POST https://keystonedev.../UserLogin (form body)
          → Keystone returns {"JSESSIONID":"xxx", "success":true, ...}
          → catchAllPost extracts JSESSIONID, calls proxyService.setSsoSession(jsessionId)
      → SessionService.setSession(jsessionId, userName)
      → POST http://localhost:3000/api/sso-session   // redundant sync for SSO path

JCEF browser (after RunScript page loads):
  → AJAX calls to relative URLs (e.g., /DirectXMLPostJSON)
  → catchAllPost fires → CookieInjector.injectCookie() adds JSESSIONID
  → Keystone receives authenticated request
```

Two places set `ssoSessionId` on `ProxyServerService`:
1. `catchAllPost` extracts it from the `UserLogin` response body
2. `postSsoSession` route accepts it explicitly (SSO / Kerberos path)

If session injection stops working, check both paths.

---

## Modifying Network Monitoring

`NetworkMonitorService` stores request/response pairs using a shared `requestId`. The Diagnostics panel pairs them by matching IDs.

Always emit both request and response events — missing either produces an unmatched orphan in `getExchanges()`:

```kotlin
val requestId = System.nanoTime().toString(36)

// BEFORE forwarding to Keystone
networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
    id = requestId,
    type = "request",
    method = "POST",
    url = targetUrl,
    body = body.take(500)   // truncate — full bodies bloat memory fast
))

// AFTER receiving response
networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
    id = requestId,         // same ID — monitor pairs them
    type = "response",
    status = response.status.value,
    body = responseBody.take(500)
))
```

The event list is capped at 500 entries (see `NetworkMonitorService`). Always truncate bodies to `take(500)` for request/response captures.

For routes that should NOT appear in the monitor (e.g., internal `/api/get-project` polling), simply omit the `networkMonitor.addEvent()` calls.

---

## Build and Test Cycle

No automated tests exist. Manual validation for proxy changes:

```bash
# 1. Compile check
./gradlew build

# 2. Launch sandbox IDE
./gradlew runIde
```

In the sandbox IDE:
1. Settings > Keyscript IDE → set `keystoneServer`, `proxyPort`
2. Click "KS: Not Logged In" status bar widget → login
3. Open a `.keyscript.js` file → click the Run gutter icon
4. Open Diagnostics tool window > Network Monitor tab
5. Confirm request + response events appear as a correlated pair

For a new route specifically:
1. `./gradlew build` — compile check
2. `./gradlew runIde` — launch sandbox
3. Trigger the route from the browser/script
4. Diagnostics > Network Monitor → verify both request and response events appear
5. If content-type is wrong: fix `ContentType.parse(...)` passthrough
6. If route returns 404: check declaration order in `configure()`
