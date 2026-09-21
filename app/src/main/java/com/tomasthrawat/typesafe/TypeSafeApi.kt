package com.tomasthrawat.typesafe

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class TypeSafeRequest(
    val endpoint: String,
    val state: String,
    val question: String,
    val type: String,
    val levels: List<String>
)

class TypeSafeApi {
    fun health(endpoint: String): Result<String> = runCatching {
        val url = URL(endpoint.removeSuffix("/") + "/api/health")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }

        try {
            val code = connection.responseCode
            val text = readResponse(connection, code)
            if (code !in 200..299) {
                throw IOException("HTTP " + code + ": " + text)
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    fun ask(request: TypeSafeRequest): Result<String> = runCatching {
        val url = URL(request.endpoint.removeSuffix("/") + "/api/ask")
        val body = JSONObject()
            .put("state", request.state)
            .put(
                "questions",
                JSONObject().put(
                    "q1",
                    JSONObject()
                        .put("type", request.type)
                        .put("instructions", request.question)
                        .also { item ->
                            when (request.type) {
                                "score" -> {
                                    val levels = JSONArray()
                                    request.levels
                                        .filter { it.isNotBlank() }
                                        .forEach(levels::put)
                                    item.put("levels", levels)
                                }
                                "choice" -> item.put("criteria", JSONObject())
                            }
                        }
                )
            )

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val text = readResponse(connection, code)
            if (code !in 200..299) {
                throw IOException("HTTP " + code + ": " + text)
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponse(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
