package com.neurasamu.build.samu_brid.server

import com.neurasamu.build.samu_brid.core.BridgePipeline
import com.neurasamu.build.samu_brid.data.LogRepo
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BridgeHttpServer(
    private val ctx: android.content.Context,
    port: Int,
    private val token: String,
    private val modelName: String
) : NanoHTTPD(port) {

    private val queue = Executors.newSingleThreadExecutor()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        LogRepo.info("HTTP ${session.method} $uri")

        // 1) CORS preflight — bina auth ke handle karo, warna browser block
        if (session.method == Method.OPTIONS) {
            val pre = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
            addCorsHeaders(pre)
            return pre
        }

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

    private fun addCorsHeaders(res: Response) {
        res.addHeader("Access-Control-Allow-Origin", "*")
        res.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE, HEAD")
        res.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept, Origin, Access-Control-Request-Method, Access-Control-Request-Headers, X-Api-Key, x-api-key")
        res.addHeader("Access-Control-Allow-Private-Network", "true")
        res.addHeader("Access-Control-Max-Age", "86400")
        res.addHeader("Access-Control-Expose-Headers", "Content-Type, Authorization")
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
        val j = try { JSONObject(body) } catch (e: Exception) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("bad json: ${e.message}"))
        }
        val isStream = j.optBoolean("stream", false)

        val msgs = parseMessages(body)
        val systemPrompt = msgs.filter { it.first == "system" }.joinToString("\n\n") { it.second }
        val lastUserIdx = msgs.indexOfLast { it.first == "user" }
        if (lastUserIdx < 0) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("no user message"))
        }
        val prompt = msgs[lastUserIdx].second
        if (prompt.isBlank()) {
            return jsonResponse(Response.Status.BAD_REQUEST, errorJson("empty user message"))
        }
        val prior = msgs.take(lastUserIdx).filter { it.first != "system" }
        LogRepo.info("HTTP prompt ${prompt.length}c, stream=$isStream, sys=${systemPrompt.length}c, prior=${prior.size}")

        val future = queue.submit<String?> {
            runBlocking { BridgePipeline.submit(prompt, "api", systemPrompt, prior, ctx).getOrNull() }
        }

        return if (isStream) {
            streamChatCompletion(future)
        } else {
            try {
                val result = future.get(320, TimeUnit.SECONDS)
                if (result == null) {
                    jsonResponse(Response.Status.INTERNAL_ERROR, errorJson("driver failed (see logs)"))
                } else {
                    LogRepo.info("HTTP returning JSON response")
                    jsonResponse(Response.Status.OK, chatCompletionJson(result))
                }
            } catch (e: Exception) {
                jsonResponse(Response.Status.INTERNAL_ERROR, errorJson("queue error: ${e.message}"))
            }
        }
    }

    private fun streamChatCompletion(future: java.util.concurrent.Future<String?>): Response {
        val pos = PipedOutputStream()
        val pis = PipedInputStream(pos)
        val response = newChunkedResponse(Response.Status.OK, "text/event-stream", pis)
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")
        addCorsHeaders(response)

        Thread {
            try {
                val writer = pos.bufferedWriter()
                val chunkId = "chatcmpl-" + System.currentTimeMillis()
                val created = System.currentTimeMillis() / 1000

                fun sendChunk(delta: String, finishReason: String?) {
                    val fr = if (finishReason == null) "null" else "\"$finishReason\""
                    writer.write("data: {\"id\":\"$chunkId\",\"object\":\"chat.completion.chunk\",\"created\":$created,\"model\":\"$modelName\",\"choices\":[{\"index\":0,\"delta\":$delta,\"finish_reason\":$fr}]}\n\n")
                    writer.flush()
                }

                sendChunk("{\"role\":\"assistant\",\"content\":\"\"}", null)

                var finished = false
                val heartbeat = Thread {
                    while (!finished) {
                        try {
                            Thread.sleep(3000)
                            if (!finished) sendChunk("{}", null)
                        } catch (_: Exception) { break }
                    }
                }
                heartbeat.start()

                val result = future.get(320, TimeUnit.SECONDS)
                finished = true
                heartbeat.interrupt()

                if (result == null) {
                    writer.write("data: {\"error\":{\"message\":\"driver failed\"}}\n\n")
                } else {
                    val escaped = JSONObject.quote(result)
                    sendChunk("{\"content\":$escaped}", null)
                    sendChunk("{}", "stop")
                }
                writer.write("data: [DONE]\n\n")
                writer.flush()
                pos.close()
            } catch (e: Exception) {
                try { pos.close() } catch (_: Exception) {}
            }
        }.start()

        return response
    }

    private fun parseMessages(body: String): List<Pair<String, String>> {
        return try {
            val arr = JSONObject(body).optJSONArray("messages") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val m = arr.optJSONObject(i) ?: return@mapNotNull null
                val role = m.optString("role", "user")
                val content = when (val c = m.opt("content")) {
                    is String -> c
                    is JSONArray -> {
                        val sb = StringBuilder()
                        for (k in 0 until c.length()) {
                            sb.append(c.optJSONObject(k)?.optString("text", "") ?: "")
                        }
                        sb.toString()
                    }
                    else -> ""
                }
                role to content
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun chatCompletionJson(content: String): String {
        val pt = 15 + content.length / 4
        val ct = content.length / 4
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
                put("prompt_tokens", pt)
                put("completion_tokens", ct)
                put("total_tokens", pt + ct)
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

    private fun jsonResponse(status: Response.Status, body: String): Response {
        val res = newFixedLengthResponse(status, "application/json; charset=utf-8", body)
        addCorsHeaders(res)
        return res
    }
}
