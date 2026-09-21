package com.tomasthrawat.typesafe

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatAttachment(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val textContent: String? = null,
    val imageDataUrl: String? = null
)

class TypeSafeApi {

    fun chat(
        endpoint: String,
        openRouterApiKey: String,
        messages: List<ChatMessage>,
        attachments: List<ChatAttachment>,
        model: String = "auto"
    ): Result<String> = runCatching {
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().apply {
                messages.forEach {
                    put(
                        JSONObject()
                            .put("role", it.role)
                            .put("content", it.content)
                    )
                }
            })
            .put("attachments", JSONArray().apply {
                attachments.forEach {
                    put(JSONObject().apply {
                        put("name", it.name)
                        put("mimeType", it.mimeType)
                        put("sizeBytes", it.sizeBytes)
                        it.textContent?.let { value ->
                            put("textContent", value)
                        }
                        it.imageDataUrl?.let { value ->
                            put("imageDataUrl", value)
                        }
                    })
                }
            })

        request(
            url = URL(endpoint.trimEnd('/') + "/api/chat"),
            method = "POST",
            body = body.toString(),
            openRouterApiKey = openRouterApiKey
        )
    }

    fun health(
        endpoint: String,
        openRouterApiKey: String
    ): Result<String> = runCatching {
        request(
            url = URL(endpoint.trim().trimEnd('/') + "/api/health"),
            method = "GET",
            body = null,
            openRouterApiKey = openRouterApiKey
        )
    }

    private fun request(
        url: URL,
        method: String,
        body: String?,
        openRouterApiKey: String?
    ): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 330_000
            useCaches = false
            setRequestProperty("Accept", "application/json")

            if (body != null) {
                doOutput = true
                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
            }

            if (!openRouterApiKey.isNullOrBlank()) {
                setRequestProperty(
                    "X-OpenRouter-API-Key",
                    openRouterApiKey.trim()
                )
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(
                        body.toByteArray(StandardCharsets.UTF_8)
                    )
                }
            }

            val code = connection.responseCode

            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val text =
                stream.bufferedReader(StandardCharsets.UTF_8).use {
                    it.readText()
                }

            if (code !in 200..299) {
                throw IOException("HTTP $code: $text")
            }

            val json = JSONObject(text)

            return json.optString("answer")
                .trim()
                .takeIf { it.isNotEmpty() }
                ?: json
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                ?: throw IOException(
                    "The server returned no assistant text."
                )
        } finally {
            connection.disconnect()
        }
    }
}
