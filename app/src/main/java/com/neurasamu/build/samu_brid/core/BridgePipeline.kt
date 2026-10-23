package com.neurasamu.build.samu_brid.core

import com.neurasamu.build.samu_brid.data.ChatLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object BridgePipeline {
    private val mutex = Mutex()

    suspend fun submit(prompt: String, source: String): Result<String> = mutex.withLock {
        ChatLog.add(ChatLog.Kind.IN, prompt, source)
        val r = BridgeDriver.sendPrompt(prompt)
        r.onSuccess { ChatLog.add(ChatLog.Kind.OUT, it, source) }
            .onFailure { ChatLog.add(ChatLog.Kind.ERR, it.message ?: "unknown", source) }
        r
    }
}
