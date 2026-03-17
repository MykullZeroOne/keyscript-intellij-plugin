# Ktor Patterns Reference

## Contents
- Server configuration
- HttpClient configuration
- Route organization
- Cookie injection
- Content-Type passthrough
- Anti-patterns

---

## Server Configuration

Always use `CIO` — it's pure-Kotlin with no Netty dependency, keeping the plugin JAR lean. Never switch to Netty without updating `build.gradle.kts`.

```kotlin
// KtorProxyServer.kt:26 — CIO, all interfaces, non-blocking
server = embeddedServer(CIO, port = proxyPort, host = "0.0.0.0") {
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Get)
    }
    routes.configure(this)
}
server!!.start(wait = false)
```

`host = "0.0.0.0"` is required because JCEF runs as a subprocess — binding to `localhost` only can prevent it from reaching the proxy on some OS configurations.

Stop with `server?.stop(1000, 2000)` — 1s graceful, 2s hard kill. The server lives inside the IDE JVM; abrupt stops leak sockets that survive until IDE restart.

---

## HttpClient Configuration

One shared `httpClient` per `ProxyRoutes` instance. Two requirements are non-negotiable:

**`expectSuccess = false`** — Without it, Ktor throws `ResponseException` on any 4xx/5xx from Keystone. The proxy must pass error responses through to the JCEF browser; throwing instead silently kills the script execution from the browser's perspective.

**Trust-all TLS** — Keystone dev/test servers use self-signed certificates. Certificate pinning would break every non-production environment.

```kotlin
// ProxyRoutes.kt:44 — class-level, never recreate per request
private val httpClient = HttpClient(CIO) {
    engine {
        https {
            trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        }
    }
    expectSuccess = false
}
```

For any new `HttpClient` that makes long-running requests (e.g., in `AuthenticationService`), add timeouts:

```kotlin
install(HttpTimeout) {
    requestTimeoutMillis = 30_000
    connectTimeoutMillis = 10_000
    socketTimeoutMillis = 30_000
}
```

---

## Route Organization

Routes are private `Route.` extension functions called from `configure()`. This makes `configure()` a readable table of contents, not a wall of lambdas.

```kotlin
// ProxyRoutes.kt:62 — configure() is the TOC
fun configure(app: Application) {
    app.routing {
        postSsoSession()         // /api/sso-session
        postDeviceId()           // /api/device-id
        postDirectXmlPostJson()  // /DirectXMLPostJSON
        for (inst in supportedInstances) { getRunScript(inst) }
        post("{path...}") { catchAllPost(call) }   // MUST be last
        get("{path...}") { catchAllGet(call) }      // MUST be last
    }
}

private fun Route.postSsoSession() {
    post("/api/sso-session") {
        val jsessionId = extractJsonField(call.receiveText(), "jsessionId")
            ?: return@post call.respond(HttpStatusCode.BadRequest, """{"error":"Missing jsessionId"}""")
        proxyService.setSsoSession(jsessionId)
        call.respondText("""{"success":true}""", ContentType.Application.Json)
    }
}
```

**Catch-all routes must be declared last.** Ktor matches in declaration order — `{path...}` before specific routes silently swallows all traffic and causes 404s that are impossible to debug.

---

## Cookie Injection

`CookieInjector` (singleton object) handles two injection points:

**Request headers** — merge JSESSIONID into the browser's existing `Cookie` header:

```kotlin
// ProxyRoutes.kt:372 — catch-all POST
header("Cookie", CookieInjector.injectCookie(
    call.request.headers["Cookie"],
    proxyService.ssoSessionId
))

// NEVER do this — wipes CSRF tokens and any other cookies the browser sent
header("Cookie", "JSESSIONID=${proxyService.ssoSessionId}")
```

**POST body** — some form-encoded requests embed `JSESSIONID` in the body:

```kotlin
// ProxyRoutes.kt:348
body = CookieInjector.replaceInBody(body, proxyService.ssoSessionId)
```

**RunScript response** — set the cookie on the initial page response so the JCEF browser stores it for all subsequent AJAX calls from the script iframe:

```kotlin
// ProxyRoutes.kt:332
if (proxyService.ssoSessionId.isNotEmpty()) {
    call.response.cookies.append("JSESSIONID", proxyService.ssoSessionId, path = "/")
}
```

The `injectCookie()` implementation strips any existing JSESSIONID before prepending the new one — this is why REPLACE not ADD is correct: the browser's stale session must never win over the server's current one.

---

## Content-Type Passthrough

Keystone returns both JSON and XML from different endpoints. Always forward the upstream `Content-Type`.

```kotlin
// GOOD — ProxyRoutes.kt:411
call.respondText(
    responseBody,
    ContentType.parse(response.headers[HttpHeaders.ContentType] ?: "application/json"),
    response.status
)

// BAD — hardcoding breaks /DirectXMLPostJSON and /SearchJSON which return XML
call.respondText(responseBody, ContentType.Application.Json)
```

For binary responses (images, JS bundles), use `respondBytes`:

```kotlin
// ProxyRoutes.kt:448
val responseBytes = response.readBytes()
call.respondBytes(
    responseBytes,
    ContentType.parse(contentType ?: "application/octet-stream"),
    response.status
)
```

---

## WARNING: HttpClient Instance Per Request

### The Problem

```kotlin
// BAD — new client on every route handler invocation
post("/DirectXMLPostJSON") {
    val client = HttpClient(CIO) { ... }
    val response = client.post(targetUrl) { ... }
    // client never closed — leaked connection pool
}
```

**Why This Breaks:**
1. Each `HttpClient(CIO)` creates a new coroutine dispatcher and connection pool
2. JBR heap is constrained inside IntelliJ — socket exhaustion under moderate script usage
3. Silent degradation: requests slow to a crawl before failing with connection errors

**The Fix:** Single class-level `private val httpClient = HttpClient(CIO) { ... }` in `ProxyRoutes`.

---

## WARNING: runBlocking Inside Ktor Route

### The Problem

```kotlin
// BAD — runBlocking inside a suspend function on the CIO dispatcher
post("/api/something") {
    val result = runBlocking { someOperation() }
}
```

**Why This Breaks:**
1. Ktor CIO routes are already coroutines on a shared dispatcher
2. `runBlocking` inside a coroutine blocks the thread, not just the coroutine
3. All proxy routes share the CIO thread pool — one blocked route stalls login, RunScript, and all AJAX calls simultaneously

**The Fix:**

```kotlin
// GOOD — route handlers are suspend; use withContext for CPU/IO-heavy ops
post("/api/something") {
    val result = withContext(Dispatchers.IO) { someBlockingOperation() }
    call.respondText(result)
}
```

---

## WARNING: Missing Request Timeouts

The proxy makes outbound requests to Keystone. Without timeouts, a slow/unreachable Keystone server hangs the proxy indefinitely, blocking the JCEF browser and making the IDE appear frozen.

Always configure `HttpTimeout` on the `HttpClient` used for outbound requests. The shared `httpClient` in `ProxyRoutes` currently has no timeouts — if adding long-running routes, configure this.
