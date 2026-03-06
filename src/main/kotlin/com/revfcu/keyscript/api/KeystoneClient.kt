package com.revfcu.keyscript.api

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class KeystoneClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient()
) {
    private val gson = Gson()
    private val mediaType = "application/x-www-form-urlencoded".toMediaType()

    /**
     * Store session parameters on the Keystone server.
     * Returns the storage ID if successful.
     */
    @Throws(IOException::class)
    fun storeSessionParams(params: Map<String, Any>): String? {
        val jsonParams = gson.toJson(params)
        val body = "value=${java.net.URLEncoder.encode(jsonParams, "UTF-8")}".toRequestBody(mediaType)
        
        // The URL for SessionStore is usually {baseUrl}/Keyscript_IDE/SessionStore
        // Assuming baseUrl already includes the instance (e.g., http://.../Development)
        val url = if (baseUrl.endsWith("/")) "${baseUrl}Keyscript_IDE/SessionStore" else "${baseUrl}/Keyscript_IDE/SessionStore"
        
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }

            val bodyString = response.body?.string() ?: throw IOException("Empty response body")
            val result = gson.fromJson(bodyString, SessionStoreResponse::class.java)
            
            return if (result.success) {
                result.id
            } else {
                null
            }
        }
    }

    /**
     * Construct the URL to run a script.
     */
    fun getRunScriptUrl(scriptPath: String, paramsId: String): String {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}Keyscript_IDE/RunScript" else "${baseUrl}/Keyscript_IDE/RunScript"
        return "$url?scriptPath=${java.net.URLEncoder.encode(scriptPath, "UTF-8")}&scriptParametersId=${java.net.URLEncoder.encode(paramsId, "UTF-8")}"
    }
}

data class SessionStoreResponse(
    val success: Boolean,
    val id: String? = null
)
