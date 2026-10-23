package com.neurasamu.build.samu_brid.core

import com.neurasamu.build.samu_brid.data.ChatLog
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

    /** Called by HTTP server when Samu Chat sends. Waits for manual reply. */
    suspend fun submit(prompt: String, source: String): Result<String> = mutex.withLock {
        ChatLog.add(ChatLog.Kind.IN, prompt, source)
        val p = Pending(++seq, prompt, source, System.currentTimeMillis(), CompletableDeferred())
        _pending.update { it + p }
        return try {
            val reply = withTimeout(300_000L) { p.completer.await() }
            ChatLog.add(ChatLog.Kind.OUT, reply, source)
            Result.success(reply)
        } catch (e: Exception) {
            _pending.update { list -> list.filterNot { it.id == p.id } }
            ChatLog.add(ChatLog.Kind.ERR, "timeout/no reply", source)
            Result.failure(e)
        }
    }

    /** Called by UI Send button. Sends text as reply to oldest pending request. */
    fun reply(text: String): Boolean {
        val p = _pending.value.firstOrNull() ?: return false
        _pending.update { it.filterNot { x -> x.id == p.id } }
        p.completer.complete(text)
        return true
    }

    /** Manual message that doesn't correspond to a pending request — used for local testing. */
    fun logManual(text: String) {
        ChatLog.add(ChatLog.Kind.IN, text, "manual")
    }
}
