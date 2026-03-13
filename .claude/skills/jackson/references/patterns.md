# Jackson Patterns Reference

## Contents
- Instantiation
- JsonNode Tree Traversal
- Fallback Field Name Extraction
- Deep Tree Search
- ObjectNode Builder
- Map-Based Serialization
- Anti-Patterns

---

## Instantiation

Always use `jacksonObjectMapper()`, not `ObjectMapper()`. The Kotlin module is required for data class support (no-arg constructor detection, nullable type handling).

```kotlin
// GOOD — Kotlin-aware, registered KotlinModule
private val mapper = jacksonObjectMapper()

// BAD — raw ObjectMapper() without KotlinModule breaks Kotlin data classes
private val mapper = ObjectMapper()
```

**All six files** in this codebase follow the same pattern: `private val mapper = jacksonObjectMapper()` as a class-level property. This avoids repeated instantiation overhead.

One exception: `RunKeyscriptService` creates a throwaway mapper inline:
```kotlin
// Acceptable for one-off use, but prefer class-level mapper for reuse
val jsonParams = jacksonObjectMapper().writeValueAsString(params.getScriptParameters())
```

---

## JsonNode Tree Traversal

Use `.path()` over `.get()` for safe traversal — `.get()` returns `null` on missing keys, `.path()` returns `MissingNode` (never throws, supports chaining).

```kotlin
// GOOD — .path() chains safely, .asText("") provides default
val serial = root.path("query").path("sequence").path("serial").asText("")
val count = root.path("totalHitCount").asInt(0)
val flag = root.path("active").asBoolean(false)

// BAD — .get() returns null, chaining throws NullPointerException
val serial = root.get("query").get("sequence").get("serial").asText()
```

**Handling single-vs-array ambiguity** — Keystone API returns single objects OR arrays for the same field:

```kotlin
// From SearchJsonParsers.kt and TableBrowserToolWindowFactory.kt
private fun asArray(node: JsonNode?): Iterable<JsonNode> {
    if (node == null) return emptyList()
    return if (node.isArray) node else listOf(node)
}

// Usage
val rows = search.get("resultRow") ?: return results
val rowList = if (rows.isArray) rows else listOf(rows)  // KeystoneApiClient pattern
```

---

## Fallback Field Name Extraction

The Keystone API uses inconsistent casing across endpoints (camelCase, SCREAMING_SNAKE_CASE, mixed). Use extension functions with vararg fallbacks:

```kotlin
// From SearchJsonParsers.kt
private fun JsonNode.textOrEmpty(vararg fieldNames: String): String {
    for (fieldName in fieldNames) {
        val node = this[fieldName] ?: continue
        if (node.isNull) continue
        return if (node.isTextual) node.asText() else node.toString().trim('"')
    }
    return ""
}

// Usage — checks camelCase then SCREAMING_SNAKE then alternatives
val description = row.textOrEmpty(
    "rowDescription", "ROW_DESCRIPTION", "description",
    "DESCRIPTION", "accountAssociation"
)
```

**Deduplication when iterating** — TableBrowserToolWindowFactory uses a `seen` set to skip duplicates from multi-response merges:

```kotlin
val seen = mutableSetOf<String>()
for (filter in filters) {
    val name = filter.textOrEmpty("filterName")
    if (name.isEmpty() || !seen.add(name)) continue  // skip blank and duplicates
    results.add(SearchFilterEntry(name, params))
}
```

---

## Deep Tree Search

Keystone wraps responses in deeply nested structures. Two utility patterns from `KeystoneApiClient.kt` and `TableBrowserToolWindowFactory.kt`:

```kotlin
// Find first occurrence of a key anywhere in the tree
private fun findDeep(node: JsonNode, key: String): JsonNode? {
    if (node.has(key)) return node.get(key)
    for (child in node) {
        if (child.isObject || child.isArray) {
            val found = findDeep(child, key)
            if (found != null) return found
        }
    }
    return null
}

// Find all occurrences and call action on each
private fun findAllDeep(node: JsonNode, key: String, action: (JsonNode) -> Unit) {
    if (node.has(key)) {
        val target = node.get(key)
        if (target.isArray) target.forEach(action) else action(target)
    }
    for (child in node) {
        if (child.isObject || child.isArray) findAllDeep(child, key, action)
    }
}
```

Copy these into any service that parses Keystone responses — the nesting depth is unpredictable.

---

## ObjectNode Builder

Prefer the fluent `createObjectNode()` API when building structured Keystone queries:

```kotlin
// From KeystoneApiClient.kt — nested object builder
fun buildSearchQuery(tableName: String, filterName: String): ObjectNode {
    val root = mapper.createObjectNode()
    val query = root.putObject("query")
    val step = query.putObject("sequence").putObject("transaction").putObject("step")
    val search = step.putObject("search")
    search.put("tableName", tableName)
    search.put("filterName", filterName)
    search.putObject("includeSelectColumns").put("option", "Y")
    search.put("returnLimit", 10)
    return root
}

val body = mapper.writeValueAsString(buildSearchQuery("SCRIPT", "BY_NAME"))
```

`putObject()` returns the child node, enabling chaining. `put()` sets primitive values.

---

## Map-Based Serialization

`DeploymentService.kt` uses `linkedMapOf` when JSON field order matters (some Keystone endpoints are order-sensitive):

```kotlin
// GOOD — linkedMapOf preserves insertion order in JSON output
val payload = linkedMapOf<String, Any>(
    "\$attr" to mapOf("sessionId" to sessionId),
    "sequence" to mapOf(
        "transaction" to mapOf(
            "step" to mapOf("search" to searchBody)
        )
    )
)
return mapper.writeValueAsString(mapOf("query" to payload))

// BAD — mapOf() (HashMap) gives non-deterministic key order
val payload = mapOf<String, Any>("sequence" to ..., "\$attr" to ...)
```

---

## Anti-Patterns

### WARNING: Map<String, Any> Deserialization

**The Problem:**

```kotlin
// BAD — DeploymentService.kt uses this with @Suppress
val json = mapper.readValue(responseBody, Map::class.java) as Map<String, Any>
val query = json["query"] as? Map<String, Any>  // unchecked cast at every level
```

**Why This Breaks:**
1. Every nested access requires an explicit `as?` cast with runtime type check
2. Jackson deserializes numbers as `Integer` or `Long` depending on value — `as Int` will fail on large values
3. Lists become `ArrayList<*>`, requiring additional casting to iterate

**The Fix:**

```kotlin
// GOOD — use readTree() and navigate with JsonNode API
val root = mapper.readTree(responseBody)
val query = root.path("query")
val serial = query.path("serial").asText("")
val count = query.path("count").asInt(0)
```

Or define a data class and use `readValue<MyResponse>(responseBody)` for structured responses.

### WARNING: Missing KotlinModule

**The Problem:**

```kotlin
// BAD — fails for Kotlin data classes with nullable properties
val mapper = ObjectMapper()
val config = mapper.readValue<BundleConfig>(file)  // MissingKotlinParameterException
```

**Why This Breaks:**
1. `ObjectMapper()` has no Kotlin module — can't instantiate data classes without no-arg constructor
2. Nullable `String?` fields are not handled; Jackson treats them as non-null Java `String`

**The Fix:** Always use `jacksonObjectMapper()`.
