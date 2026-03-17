# Ktor Patterns Reference

## Contents
- Server configuration
- HttpClient configuration
- Route organization
- Cookie injection
- Anti-patterns

---

## Server Configuration

Always use `CIO` engine — it's pure-Kotlin with no Netty dependency, essential for keeping the plugin JAR lean. Never switch to Netty without updating `build.gradle.kts` dependencies.

```kotlin
// GOOD — CIO, localhost-only, non-blocking start
server = embeddedServer(CIO, port = proxyPort, host = "localhost") {
    install(CORS) { anyHost(); allowHeader(HttpHeaders.ContentType) }
    routes.configure(this)
}
server!!.start(wait = false)
```

Stop with grace period: `server?.stop(1000, 2000)` — 1s graceful, 2s hard stop. This matters because the server runs inside the IDE process; abrupt stops can leak sockets.

---

## HttpClient Configuration

Two clients exist in this codebase: one in `ProxyRoutes` (long-lived, shared) and one in `AuthenticationService` (per-service). Both require identical TLS and `expectSuccess = false`.

**`expectSuccess = false` is mandatory.** Without it, Ktor throws `ResponseException` on any 4xx/5xx from Keystone, which breaks the proxy's passthrough behavior — the client never sees the actual error body.

```kotlin
// GOOD — expectSuccess=false + trust-all TLS for Keystone's self-signed cert
val httpClient = HttpClient(CIO) {
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

`AuthenticationService` additionally sets timeouts — do the same for any new long-running client:

```kotlin
install(HttpTimeout) {
    requestTimeoutMillis = 30_000
    connectTimeoutMillis = 10_000
    socketTimeoutMillis = 30_000
}
```

---

## Route Organization

Routes are defined as **private extension functions on `Route`** inside `ProxyRoutes`, then called from `configure()`. This keeps `configure()` readable as a table of contents without scattering route logic.

```kotlin
fun configure(app: Application) {
    app.routing {
        postSsoSession()      // /api/sso-session
        postDeviceId()        // /api/device-id
        postDirectXmlPostJson()
        for (inst in supportedInstances) { getRunScript(inst) }
        post("{path...}") { catchAllPost(call) }   // MUST be last
        get("{path...}") { catchAllGet(call) }      // MUST be last
    }
}

private fun Route.postSsoSession() {
    post("/api/sso-session") {
        val body = call.receiveText()
        // ...
    }
}
```

**Catch-all routes must be declared last.** Ktor matches routes in declaration order — placing `{path...}` before specific routes silently swallows them.

---

## Cookie Injection

`CookieInjector.injectCookie()` merges the JSESSIONID into the existing `Cookie` header from the browser. Never replace the entire header — the browser may send other cookies (e.g., CSRF tokens).

```kotlin
// GOOD — merges JSESSIONID into existing cookies
header("Cookie", CookieInjector.injectCookie(
    call.request.headers["Cookie"],
    proxyService.ssoSessionId
))

// BAD — wipes out any other cookies the browser sent
header("Cookie", "JSESSIONID=${proxyService.ssoSessionId}")
```

For the RunScript response, set the cookie on the response so the JCEF browser stores it for subsequent AJAX calls:

```kotlin
call.response.cookies.append("JSESSIONID", proxyService.ssoSessionId, path = "/")
```

---

## Content-Type Passthrough

When forwarding responses, always pass through the upstream `Content-Type` — Keystone returns both JSON and XML from different endpoints.

```kotlin
// GOOD
call.respondText(
    responseBody,
    ContentType.parse(response.headers[HttpHeaders.ContentType] ?: "application/json"),
    response.status
)

// BAD — hardcodes JSON, breaks XML endpoints like /DirectXMLPostJSON
call.respondText(responseBody, ContentType.Application.Json)
```

---

## WARNING: HttpClient Instance Per Route

### The Problem

```kotlin
// BAD — new client on every request
post("/DirectXMLPostJSON") {
    val client = HttpClient(CIO) { ... }
    val response = client.post(targetUrl) { ... }
    // client never closed — connection pool leak
}
```

**Why This Breaks:**
1. Each `HttpClient(CIO)` creates a new connection pool and coroutine dispatcher
2. Connections are never reused — high Keystone load causes socket exhaustion
3. Under IDE's JBR heap constraints, this triggers OOM under moderate usage

**The Fix:**

```kotlin
// GOOD — single shared client on ProxyRoutes class
private val httpClient = HttpClient(CIO) { ... }
```

---

## WARNING: Blocking Call on Ktor Dispatcher

### The Problem

```kotlin
// BAD — runBlocking inside a Ktor route suspending function
post("/api/something") {
    val result = runBlocking { someHeavyOperation() }
}
```

**Why This Breaks:**
1. Ktor CIO uses a coroutine dispatcher; `runBlocking` inside a coroutine deadlocks under load
2. All proxy routes share the CIO thread pool — one blocked route stalls all others

**The Fix:**

```kotlin
// GOOD — all route handlers are already suspend; use withContext for IO
post("/api/something") {
    val result = withContext(Dispatchers.IO) { someHeavyOperation() }
    call.respondText(result)
}
```
