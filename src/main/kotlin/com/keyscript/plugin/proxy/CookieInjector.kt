package com.keyscript.plugin.proxy

/**
 * Handles JSESSIONID cookie injection/replacement.
 * Critical fix: always REPLACE any existing JSESSIONID (not just add).
 */
object CookieInjector {

    /**
     * Replace or inject JSESSIONID in a cookie header string.
     */
    fun injectCookie(existingCookie: String?, ssoSessionId: String): String {
        if (ssoSessionId.isEmpty()) return existingCookie ?: ""

        val cleaned = (existingCookie ?: "")
            .replace(Regex("""JSESSIONID=[^;]*(;\s*)?"""), "")
            .replace(Regex(""";\\s*$"""), "")
            .trim()

        return if (cleaned.isNotEmpty()) {
            "JSESSIONID=$ssoSessionId; $cleaned"
        } else {
            "JSESSIONID=$ssoSessionId"
        }
    }

    /**
     * Replace JSESSIONID in a POST body string (form-encoded).
     */
    fun replaceInBody(body: String, ssoSessionId: String): String {
        if (ssoSessionId.isEmpty() || !body.contains("JSESSIONID=")) return body
        return body.replace(Regex("""JSESSIONID=[^&]+"""), "JSESSIONID=$ssoSessionId")
    }
}
