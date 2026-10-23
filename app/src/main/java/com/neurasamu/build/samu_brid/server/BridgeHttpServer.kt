package com.neurasamu.build.samu_brid.server

import com.neurasamu.build.samu_brid.core.BridgeDriver
import com.neurasamu.build.samu_brid.data.LogRepo
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BridgeHttpServer(
    port: Int,
    private val token: String,
    private val modelName: String
) : NanoHTTPD(port) {

    private val queue = Executors.newSingleThreadExecutor()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        LogRepo.info("HTTP ${session.method} $uri")

        if (!authOk(session)) {
            LogRepo.warn("HTTP auth rejected")
            return jsonResponse(Response.Status.UNAUTHORIZED, errorJson("invalid api token"))
        }

        return try {
            when {
                uri == "/v1/models" && session.method == Method.GET -> handleModels()
                uri == "/v1/chat/completions" && session.method == Method.POST -> handleChat(session)
                uri == "/health" -> jsonResponse(Response.Status.OK, "{\"ok\":true}")
                else -> jsonResponse(Response.Status.NOT_FOUND, errorJson("not found: $uri"))
            }
        } catch (t: Throwable) {
            LogRepo.error("HTTP handler crash: ${t.message}")
            jsonResponse(Response.Status.INTERNAL_ERROR, errorJson("server error: ${t.message}"))
        }
    }

    override fun stop() {
        try { queue.shutdownNow() } catch (_: Exception) {}
        super.stop()
    }

    private fun authOk(session: IHTTPSession): Boolean {
        if (token.isBlank()) return true
        val h = session.headers["authorization"] ?: session.headers["Authorization"] ?: return false
        return h.equals("Bearer $token", ignoreCase = true)
    }

    private fun handleModels(): Response {
        val j = JSONObject().apply {
            put("object", "list")
            put("data", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", modelName)
                    put("object", "model")
                    put("created", System.currentTimeMillis() / 1000)
                    put("owned_by", "samu-bridge")
                })
            })
        }
        return jsonResponse(Response.Status.OK, j.toString())
    }

    private fun handleChat(session: IHTTPSession): Response {
        val map = HashMap<String, String>()
        try { session.parseBody(map) } catch (e: Exception) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("bad body: ${e.message}"))
        }
        val body = map["postData"] ?: ""
        val prompt = try {
            extractLastUserMessage(body)
        } catch (e: Exception) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("bad json: ${e.message}"))
        }
        if (prompt.isBlank()) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("no user message"))
        }
        LogRepo.info("HTTP prompt ${prompt.length} chars")

        return try {
            val future = queue.submit<String?> {
                runBlocking { BridgeDriver.sendPrompt(prompt).getOrNull() }
            }
            val result = future.get(120, TimeUnit.SECONDS)
            if (result == null) {
                jsonResponse(Response.Status.INTERNAL_ERROR, errorJson("driver failed (see logs)"))
            } else {
                jsonResponse(Response.Status.OK, chatCompletionJson(result))
            }
        } catch (e: Exception) {
            jsonResponse(Response.Status.INTERNAL_ERROR, errorJson("queue error: ${e.message}"))
        }
    }

    private fun extractLastUserMessage(body: String): String {
        val j = JSONObject(body)
        val arr = j.optJSONArray("messages") ?: return ""
        for (i in arr.length() - 1 downTo 0) {
            val m = arr.optJSONObject(i) ?: continue
            if (m.optString("role") != "user") continue
            val c = m.opt("content") ?: continue
            if (c is String) return c
            if (c is JSONArray) {
                val sb = StringBuilder()
                for (k in 0 until c.length()) {
                    val p = c.optJSONObject(k) ?: continue
                    sb.append(p.optString("text", ""))
                }
                return sb.toString()
            }
        }
        return ""
    }

    private fun chatCompletionJson(content: String): String {
        return JSONObject().apply {
            put("id", "chatcmpl-" + System.currentTimeMillis())
            put("object", "chat.completion")
            put("created", System.currentTimeMillis() / 1000)
            put("model", modelName)
            put("choices", JSONArray().apply {
                put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", content)
                    })
                    put("finish_reason", "stop")
                })
            })
            put("usage", JSONObject().apply {
                put("prompt_tokens", 0)
                put("completion_tokens", 0)
                put("total_tokens", 0)
            })
        }.toString()
    }

    private fun errorJson(msg: String): String =
        JSONObject().apply {
            put("error", JSONObject().apply {
                put("message", msg)
                put("type", "invalid_request_error")
            })
        }.toString()

    private fun jsonResponse(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json", body)
}
