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

- [ ] 1. Define the route as a private `Route.` extension in `ProxyRoutes.kt`
- [ ] 2. Register it in `configure()` **before** the catch-all routes
- [ ] 3. Inject JSESSIONID via `CookieInjector.injectCookie()`
- [ ] 4. Emit request/response events to `networkMonitor`
- [ ] 5. Pass through `Content-Type` from upstream response
- [ ] 6. Build: `./gradlew build` — fix any compile errors
- [ ] 7. Run sandbox IDE: `./gradlew runIde` — manually verify the route

**Route template:**

```kotlin
private fun Route.postMyNewRoute() {
    post("/api/my-route") {
        val body = call.receiveText()
        val targetUrl = "$proxyUrl/$currentInstance/MyRoute"
        val requestId = System.nanoTime().toString(36)

        networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
            id = requestId, type = "request", method = "POST",
            url = "/api/my-route", body = body.take(500)
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
        // ... existing routes
        postMyNewRoute()          // add before catch-alls
        post("{path...}") { catchAllPost(call) }
        get("{path...}") { catchAllGet(call) }
    }
}
```

---

## Debugging Proxy Startup Failures

The proxy starts lazily via `ProxyServerService.ensureStarted()`. It fails silently in production — check IntelliJ's "Run" panel for log output.

**Common causes:**

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Address already in use` | Port 3000 taken by another process | Change `proxyPort` in settings or kill the process |
| Preview blank, no network events | `ensureStarted()` never called | Trigger run/login to force startup |
| Routes return 404 | Catch-all fires before specific route | Move specific route before `{path...}` in `configure()` |
| SSL handshake error | TrustManager not applied | Confirm `expectSuccess = false` and `trustManager` in `engine {}` block |

**Verify proxy is running:**

```kotlin
// ProxyServerService.getProxyBaseUrl() starts the server if needed
val url = ProxyServerService.getInstance(project).getProxyBaseUrl()
// Should return "http://localhost:3000" (or configured port)
```

Iterate-until-pass:
1. Change proxy code
2. Run `./gradlew build` — verify no compile errors
3. Launch `./gradlew runIde`
4. Trigger proxy start (login or run a script)
5. Check Run panel logs for `"Proxy server started on port ..."` — if missing, check `ProxyServerService` logs
6. If startup fails, fix root cause and repeat from step 1

---

## Authentication Flow Walkthrough

Understanding the full auth flow is required when modifying login, SSO, or session injection.

```
User clicks Login
    → AuthenticationService.login()
        → ProxyServerService.ensureStarted()          // start proxy if not running
        → GET http://localhost:3000/{instance}         // set currentInstance on proxy
        → POST http://localhost:3000/api/device-id     // register device ID
        → POST http://localhost:3000/UserLogin         // proxy forwards to Keystone
            → ProxyRoutes.catchAllPost()
                → POST https://keystonedev.../UserLogin (with form body)
                → Keystone returns {"JSESSIONID":"...", "success":true, ...}
        → Parse JSESSIONID from response
        → SessionService.setSession(jsessionId, userName)
        → ProxyServerService.setSsoSession(jsessionId)
        → POST http://localhost:3000/api/sso-session   // sync session to proxy state
```

When JCEF browser makes subsequent AJAX calls, `catchAllPost` injects the stored `ssoSessionId` via `CookieInjector`.

---

## Modifying Network Monitoring

`NetworkMonitorService` captures request/response pairs using a shared `requestId`. Events are stored in a 500-entry `CopyOnWriteArrayList` and displayed in the Diagnostics panel.

To add monitoring to a new route, always use the paired-ID pattern so request and response correlate in the UI:

```kotlin
val requestId = System.nanoTime().toString(36)

// BEFORE forwarding
networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
    id = requestId,
    type = "request",
    method = "POST",
    url = targetUrl,
    body = body.take(500)   // truncate to avoid memory bloat
))

// AFTER forwarding
networkMonitor.addEvent(NetworkMonitorService.NetworkEvent(
    id = requestId,         // same ID — monitor pairs them as NetworkExchange
    type = "response",
    status = response.status.value,
    body = responseBody.take(500)
))
```

Events without a matching pair show as unmatched in `getExchanges()`. Always emit both or neither.

---

## Build and Test Cycle

No automated test suite exists yet. Manual validation steps:

```bash
# 1. Compile check
./gradlew build

# 2. Launch sandbox IDE with plugin
./gradlew runIde

# 3. In sandbox IDE:
#    Settings > Keyscript IDE → set keystoneServer, proxyPort
#    Click status bar "KS: Not Logged In" → login
#    Open a .keyscript.js file → click Run gutter icon
#    Check Diagnostics panel → Network Monitor tab for captured events
```

For proxy route changes specifically:
1. `./gradlew build` — verify compilation
2. `./gradlew runIde` — launch sandbox
3. Open Diagnostics > Network Monitor
4. Trigger the affected route (login/run/search)
5. Confirm request+response events appear as a correlated pair
6. If missing: add `networkMonitor.addEvent(...)` calls; if wrong content-type: fix `ContentType.parse(...)` passthrough
