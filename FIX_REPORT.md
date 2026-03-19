# Keyscript IntelliJ Plugin — Fix Report

Grouped by feature, ordered by severity within each group.

---

## Proxy Server

| # | Severity | Issue | Location | Fix |
|---|----------|-------|----------|-----|
| 1 | **Critical** | Proxy binds to `0.0.0.0` — accessible from entire network | `proxy/KtorProxyServer.kt:34` | Bind to `127.0.0.1` |
| 2 | **Critical** | `CORS { anyHost() }` allows any origin to hit the proxy | `proxy/KtorProxyServer.kt:35–39` | Restrict CORS to `http://localhost:*` origins |
| 3 | **Critical** | Trust-all TLS certificate validation disables HTTPS security | `proxy/ProxyRoutes.kt:48–52` | Remove trust-all manager; add optional self-signed cert toggle in settings |
| 4 | **High** | `httpClient` in ProxyRoutes is never closed | `proxy/ProxyRoutes.kt` | Close client when `KtorProxyServer.stop()` is called |
| 5 | **Medium** | `ideParamsData` is an unbounded `ConcurrentHashMap` — entries never evicted | `proxy/ProxyRoutes.kt:36` | Add LRU eviction (cap at ~100 entries) or time-based expiry |
| 6 | **Medium** | MAC addresses exposed via `/GetDeviceInformation` on network-accessible proxy | `proxy/ProxyRoutes.kt` | Resolves automatically once proxy is bound to localhost |

---

## Authentication Service

| # | Severity | Issue | Location | Fix |
|---|----------|-------|----------|-----|
| 7 | **Critical** | Trust-all TLS certificate validation | `services/AuthenticationService.kt:40–44` | Remove trust-all manager; add configurable self-signed cert option |
| 8 | **High** | `HttpClient` never closed — thread/connection pool leak per project | `services/AuthenticationService.kt:31–47` | Implement `Disposable`, close client in `dispose()` |
| 9 | **Medium** | `deviceId` JSON injection — manual string escaping only replaces `"` | `services/AuthenticationService.kt:79` | Use proper JSON serialization (Jackson `ObjectMapper`) |

---

## Login / Session UI

| # | Severity | Issue | Location | Fix |
|---|----------|-------|----------|-----|
| 10 | **High** | `runBlocking` on EDT-adjacent path in LoginDialog risks UI freeze | `actions/LoginAction.kt:193` | Use `executeOnPooledThread` + coroutine `launch` |
| 11 | **High** | `runBlocking` on EDT-adjacent path in status bar instance switch | `statusbar/LoginStatusBarWidgetFactory.kt:146` | Use async pattern instead of `runBlocking` |
| 12 | **Medium** | Password converted to immutable `String` — can't be zeroed from memory | `actions/LoginAction.kt:175` | Pass `CharArray` through call chain; zero array after use |
| 13 | **Medium** | Hardcoded colors instead of theme-aware `JBColor` | `actions/LoginAction.kt` (LoginDialog) | Replace `Color(0xE5, 0x6B, 0x6B)` etc. with `JBColor` |

---

## Project Service

| # | Severity | Issue | Location | Fix |
|---|----------|-------|----------|-----|
| 14 | **High** | `CoroutineScope(Dispatchers.Default)` never cancelled on project close | `services/KeyscriptProjectService.kt:23` | Implement `Disposable`, cancel scope in `dispose()` |

---

## Bundle Watch Service

| # | Severity | Issue | Location | Fix |
|---|----------|-------|----------|-----|
| 15 | **High** | `runBlocking { delay(500) }` inside `synchronized` block — deadlock risk | `services/BundleWatchService.kt:82` | Replace with callback, `CompletableFuture`, or scheduled executor |

---

## Preview Editor

| # | Severity | Issue | Location | Fix |
|---|----------|-------|----------|-----|
| 16 | **Medium** | `executeOnPooledThread { runBlocking { ... } }` — wasteful pattern | `KeyscriptPreviewFileEditor` | Use coroutines directly |
| 17 | **Low** | 100ms reload timer in `reloadPreservingState()` is a fragile race condition | `KeyscriptPreviewFileEditor` | Use a completion callback or promise instead of fixed delay |

---

## Network Monitor Service

| # | Severity | Issue | Location | Fix |
|---|----------|-------|----------|-----|
| 18 | **Low** | `CopyOnWriteArrayList.removeAt(0)` is O(n) for bounded event list | `services/NetworkMonitorService.kt` | Replace with `ArrayDeque` or `LinkedList` |

---

## Build & Marketplace Configuration

| # | Severity | Issue | Location | Fix |
|---|----------|-------|----------|-----|
| 19 | **Medium** | `instrumentCode = false` disables null-safety runtime checks | `build.gradle.kts:70` | Enable `instrumentCode = true` and fix resulting issues |
| 20 | **Medium** | `untilBuild = "253.*"` restricts plugin to single IDE major version | `build.gradle.kts:78` | Widen to `"254.*"` or remove with API compat testing |
| 21 | **Low** | `buildSearchableOptions` disabled — settings not discoverable in Search Everywhere | `build.gradle.kts:98–100` | Enable and ensure it completes without errors |
| 22 | **Low** | No `<change-notes>` element in `plugin.xml` | `plugin.xml` | Add `<change-notes>` with release notes |
| 23 | **Low** | Vendor email doesn't match vendor name ("Madd Robot" / `msmith@revfcu.com`) | `plugin.xml:4` | Use consistent vendor identity |

---

## Cross-Cutting / General

| # | Severity | Issue | Location | Fix |
|---|----------|-------|----------|-----|
| 24 | **Low** | Multiple `jacksonObjectMapper()` instances across services | Various services | Share a single `ObjectMapper` instance |
| 25 | **Low** | No test suite exists | `src/test/kotlin` (missing) | Add unit tests for services and API parsing logic |
| 26 | **Low** | JSESSIONID partially logged (truncated to 8 chars) | Multiple locations | Remove session ID from logs entirely |

---

## Summary by Severity

| Severity | Count |
|----------|-------|
| Critical | 3 |
| High | 5 |
| Medium | 7 |
| Low | 7 |
| **Total** | **22** |

### Recommended Fix Order

1. **Critical (do first):** Fix proxy binding, CORS, and trust-all TLS (items 1–3, 7)
2. **High (do next):** Close HTTP clients, cancel coroutine scopes, remove `runBlocking` from EDT paths (items 4, 8, 10–11, 14–15)
3. **Medium (before release):** JSON injection, password handling, theme colors, build config (items 5, 9, 12–13, 16, 19–20)
4. **Low (nice-to-have):** Searchable options, change notes, vendor info, shared ObjectMapper, tests (items 17–18, 21–26)
