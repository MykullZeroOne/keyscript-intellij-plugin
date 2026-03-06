package com.revfcu.keyscript.api

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class KeybridgeClient(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val gson = Gson()
    private val mediaType = "application/json".toMediaType()

    @Throws(IOException::class)
    fun logon(userName: String, deviceName: String, password: String): String? {
        val logonParams = LogonParams(userName, deviceName, password)
        val logonQuery = LogonQuery(logonParams)
        val logonRequest = LogonRequest(logonQuery)
        
        val jsonRequest = gson.toJson(logonRequest)
        val body = jsonRequest.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(baseUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }

            val bodyString = response.body?.string() ?: throw IOException("Empty response body")
            val logonResponse = gson.fromJson(bodyString, LogonResponse::class.java)
            
            return if (logonResponse.error != null) {
                throw IOException("Keybridge API Error: ${logonResponse.error.message}")
            } else {
                logonResponse.result?.logon?.sessionId
            }
        }
    }
}
