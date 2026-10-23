package com.neurasamu.build.samu_brid.core

import android.content.Context
import com.neurasamu.build.samu_brid.data.ChatLog
import com.neurasamu.build.samu_brid.server.NotificationHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

object BridgePipeline {
    private val mutex = Mutex()

    data class Pending(
        val id: Long,
        val prompt: String,
        val source: String,
        val at: Long,
        val completer: CompletableDeferred<String>
    )

    private val _pending = MutableStateFlow<List<Pending>>(emptyList())
    val pending = _pending.asStateFlow()

    private var seq = 0L

    /**
     * Full messages = list of (role, content) from client.
     * Emits SYSTEM/CTX entries for context, IN for last user message.
     */
    suspend fun submit(
        prompt: String,
        source: String,
        systemPrompt: String = "",
        priorMessages: List<Pair<String, String>> = emptyList(),
        context: Context? = null
    ): Result<String> = mutex.withLock {
        if (systemPrompt.isNotBlank()) ChatLog.add(ChatLog.Kind.SYSTEM, systemPrompt, source)
        priorMessages.forEach { (role, content) ->
            if (content.isNotBlank()) ChatLog.add(ChatLog.Kind.CTX, "[$role] $content", source)
        }
        ChatLog.add(ChatLog.Kind.IN, prompt, source)

        val p = Pending(++seq, prompt, source, System.currentTimeMillis(), CompletableDeferred())
        _pending.update { it + p }

        if (context != null) {
            NotificationHelper.showReplyNotification(context, p.id.toInt(), prompt, source)
        }

        return try {
            val reply = withTimeout(300_000L) { p.completer.await() }
            ChatLog.add(ChatLog.Kind.OUT, reply, source)
            Result.success(reply)
        } catch (e: Exception) {
            _pending.update { list -> list.filterNot { it.id == p.id } }
            ChatLog.add(ChatLog.Kind.ERR, "timeout/no reply", source)
            Result.failure(e)
        } finally {
            if (context != null) NotificationHelper.cancel(context, p.id.toInt())
        }
    }

    fun reply(text: String): Boolean {
        val p = _pending.value.firstOrNull() ?: return false
        _pending.update { it.filterNot { x -> x.id == p.id } }
        p.completer.complete(text)
        return true
    }
}
