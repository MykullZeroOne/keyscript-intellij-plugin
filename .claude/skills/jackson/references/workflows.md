# Jackson Workflows Reference

## Contents
- Adding a New Keystone API Call
- Adding a New Config Field to BundleConfig
- Extending a Data Response Parser
- Debugging JSON Parse Failures
- Adding Content Negotiation to Ktor Routes

---

## Adding a New Keystone API Call

Use this workflow when adding a method to `KeystoneApiClient.kt` or a service that calls the Keystone API.

Copy this checklist and track progress:
- [ ] Step 1: Define the request builder (ObjectNode or linkedMapOf)
- [ ] Step 2: Define the response data class (or reuse `ApiResult`)
- [ ] Step 3: Call `mapper.writeValueAsString()` for the request body
- [ ] Step 4: Call `mapper.readTree()` on the response and navigate with `findDeep()`
- [ ] Step 5: Return `null` or `ApiResult(success = false)` on parse exception

```kotlin
// 1. Request builder — use ObjectNode for complex nesting
fun buildTableQuery(tableName: String): String {
    val root = mapper.createObjectNode()
    val search = root.putObject("query").putObject("sequence")
        .putObject("transaction").putObject("step").putObject("search")
    search.put("tableName", tableName)
    search.putObject("includeSelectColumns").put("option", "Y")
    return mapper.writeValueAsString(root)
}

// 2. Data class — no annotations needed if field names match
data class TableResult(val name: String, val description: String)

// 3–5. Parse response
fun parseTableResult(responseBody: String): TableResult? {
    return try {
        val root = mapper.readTree(responseBody)
        val record = findDeep(root, "record") ?: return null
        TableResult(
            name = record.path("tableName").asText(""),
            description = record.path("tableDescription").asText("")
        )
    } catch (e: Exception) {
        log.warn("Failed to parse table result", e)
        null
    }
}
```

**Validate:** After writing, confirm field names against an actual Keystone API response. The API returns inconsistent casing — use the `textOrEmpty(vararg)` extension if multiple field name variants are possible.

---

## Adding a New Config Field to BundleConfig

`BundleConfig` in `BundleService.kt` maps directly to `keyscript.bundle.json`. Field names must match the JSON exactly.

```kotlin
// BEFORE
data class BundleConfig(
    val entry: String = "src/index.jsx",
    val minify: Boolean = false
)

// AFTER — add field with a default so existing configs don't break
data class BundleConfig(
    val entry: String = "src/index.jsx",
    val minify: Boolean = false,
    val sourcemap: Boolean = true  // new field — default handles missing key in old files
)
```

**No annotation needed** — `jacksonObjectMapper()` with KotlinModule handles `null`-safe defaults automatically. Fields in the JSON that don't exist in the data class are silently ignored (Jackson's `FAIL_ON_UNKNOWN_PROPERTIES` defaults to `false` in the Kotlin module).

**Validate:**

```bash
# Confirm existing keyscript.bundle.json still parses after adding field
./gradlew runIde  # check IDE log for BundleService warnings
```

---

## Extending a Data Response Parser

When a Keystone endpoint adds new fields to an existing response, extend the parser without breaking existing callers.

Pattern from `TableBrowserToolWindowFactory.kt`:

```kotlin
// BEFORE
private data class TableEntry(val name: String, val description: String)

private fun parseTableList(json: String): List<TableEntry> {
    val root = mapper.readTree(json)
    for (table in asArray(findDeep(root, "table"))) {
        results.add(TableEntry(
            name = table.textOrEmpty("tableName"),
            description = table.textOrEmpty("tableDescription")
        ))
    }
}

// AFTER — add field with default; parser change is backward-compatible
private data class TableEntry(
    val name: String,
    val description: String,
    val viewGroup: String = ""  // new — existing call sites still compile
)

// Add extraction in the loop
results.add(TableEntry(
    name = table.textOrEmpty("tableName"),
    description = table.textOrEmpty("tableDescription"),
    viewGroup = table.textOrEmpty("viewGroup")  // returns "" if missing
))
```

---

## Debugging JSON Parse Failures

When Jackson throws or returns unexpected empty values:

**Step 1 — Log the raw response before parsing:**

```kotlin
fun parseResponse(body: String): MyResult? {
    log.debug("Raw response: $body")  // add temporarily
    return try {
        val root = mapper.readTree(body)
        // ...
    } catch (e: JsonProcessingException) {
        log.warn("JSON parse failed at: ${e.location}, message: ${e.message}", e)
        null
    }
}
```

**Step 2 — Check for single-vs-array ambiguity:**

Keystone returns `"resultRow": {...}` (object) when there's one result and `"resultRow": [...]` (array) for multiple. Always normalize:

```kotlin
val rows = root.path("resultRow")
val rowList = if (rows.isArray) rows else if (rows.isMissingNode || rows.isNull) emptyList<JsonNode>() else listOf(rows)
```

**Step 3 — Confirm field name casing:**

Use the Diagnostics panel (Network Monitor) to capture the raw HTTP response from Keystone. Compare field names exactly — the API mixes `camelCase`, `SCREAMING_SNAKE_CASE`, and `PascalCase` across endpoints.

**Step 4 — Validate with readTree + print:**

```kotlin
val root = mapper.readTree(responseBody)
println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root))  // pretty-print for inspection
```

---

## Adding Content Negotiation to Ktor Routes

The `ktor-serialization-jackson-jvm` dependency is declared in `build.gradle.kts` but content negotiation is **not explicitly installed** in `KtorProxyServer.kt`. Current routes use `call.respondText(jsonString, ContentType.Application.Json)` with pre-serialized strings.

If you need Ktor to serialize response objects automatically:

```kotlin
// In KtorProxyServer.kt — install ContentNegotiation plugin
install(ContentNegotiation) {
    jackson {
        // configure the shared ObjectMapper here if needed
        enable(SerializationFeature.INDENT_OUTPUT)
    }
}

// Then in routes — respond with any serializable object directly
call.respond(MyDataClass(success = true, data = result))
```

**WARNING:** Adding `ContentNegotiation` changes how ALL routes respond. Existing routes using `call.respondText()` are unaffected, but routes using `call.respond()` will now serialize via Jackson. Test proxy routes after adding this to ensure Keystone responses pass through unchanged.

See the **ktor** skill for full Ktor routing patterns.
