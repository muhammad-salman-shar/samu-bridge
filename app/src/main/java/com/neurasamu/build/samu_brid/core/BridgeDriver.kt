package com.neurasamu.build.samu_brid.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.neurasamu.build.samu_brid.accessibility.BridgeAccessibilityService
import com.neurasamu.build.samu_brid.data.LogRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object BridgeDriver {

    suspend fun sendPrompt(prompt: String, timeoutMs: Long = 90_000L): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val svc = BridgeAccessibilityService.instance
                    ?: error("Accessibility not connected")
                val pkg = BridgeAccessibilityService.targetPackage
                if (pkg.isBlank()) error("Target app not selected")

                LogRepo.info("Driver: start (${prompt.length} chars)")

                AppLauncher.launchApp(svc.applicationContext, pkg)
                delay(1500)

                var root: AccessibilityNodeInfo? = null
                for (i in 0 until 12) {
                    root = svc.getTargetRoot(); if (root != null) break; delay(300)
                }
                root ?: run {
                    LogRepo.error("Root missing. Tree:\n${svc.dumpActiveTree().take(1500)}")
                    error("Target window not found")
                }

                delay(400)

                val baseline = svc.collectAllTexts()
                LogRepo.info("Driver: baseline ${baseline.size} texts")

                if (!injectText(svc, prompt)) error("Text injection failed")
                LogRepo.info("Driver: text injected")

                delay(500)

                // Send
                var sent = false
                val sendBtn = svc.findSendButton()
                if (sendBtn != null && sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    sent = true; LogRepo.info("Driver: send via button")
                }
                if (!sent) {
                    val inp = svc.findInputNode() ?: svc.getFocusedInput()
                    if (inp != null) {
                        val ib = Rect(); inp.getBoundsInScreen(ib)
                        svc.tapAt(ib.right + 40f, ib.centerY().toFloat())
                        LogRepo.info("Driver: send via coordinate tap")
                        sent = true
                    }
                }
                if (!sent) error("Send action failed")
                delay(900)

                val post = svc.collectAllTexts()
                val reply = svc.waitForResponse(post, timeoutMs)
                    ?: error("Timeout — no response in ${timeoutMs}ms")
                LogRepo.info("Driver: response ${reply.length} chars")
                reply
            }.onFailure { LogRepo.error("Driver fail: ${it.message}") }
        }

    private suspend fun injectText(svc: BridgeAccessibilityService, prompt: String): Boolean {
        // Step 1: direct hit
        val found = svc.findInputNode()
        svc.findEditableIn(found)?.let { n ->
            if (trySetText(n, prompt)) { LogRepo.info("inject: SET_TEXT direct"); return true }
        }
        found?.let { n ->
            if (trySetText(n, prompt)) { LogRepo.info("inject: SET_TEXT on wrapper"); return true }
        }

        // Step 2: tap the candidate (focus), wait for IME, retry
        if (found != null) {
            svc.tapNode(found)
        } else {
            val b = svc.getScreenBounds()
            svc.tapAt(b.width() * 0.4f, b.height() * 0.88f)
        }
        delay(900)

        svc.getFocusedInput()?.let { f ->
            if (trySetText(f, prompt)) { LogRepo.info("inject: SET_TEXT focused"); return true }
            if (tryPaste(f, svc, prompt)) { LogRepo.info("inject: PASTE focused"); return true }
        }
        svc.findInputNode()?.let { n ->
            svc.findEditableIn(n)?.let { e -> if (trySetText(e, prompt)) { LogRepo.info("inject: SET_TEXT refind"); return true } }
        }

        // Step 3: long-press → Paste context menu
        if (found != null) svc.tapNode(found) else {
            val b = svc.getScreenBounds(); svc.tapAt(b.width() * 0.4f, b.height() * 0.88f)
        }
        delay(600)
        val focused = svc.getFocusedInput()
        if (focused != null) {
            val b = Rect(); focused.getBoundsInScreen(b)
            svc.longPressAt(b.centerX().toFloat(), b.centerY().toFloat())
            delay(700)
            copyToClipboard(svc.applicationContext, prompt)
            svc.findPasteMenuItem()?.let { p ->
                if (p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    LogRepo.info("inject: paste menu"); return true
                }
            }
        }
        LogRepo.warn("inject: all strategies failed")
        return false
    }

    private fun trySetText(n: AccessibilityNodeInfo, text: String): Boolean {
        try { n.refresh() } catch (_: Exception) {}
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return try {
            n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (_: Exception) { false }
    }

    private fun tryPaste(n: AccessibilityNodeInfo, svc: BridgeAccessibilityService, text: String): Boolean {
        copyToClipboard(svc.applicationContext, text)
        return try { n.performAction(AccessibilityNodeInfo.ACTION_PASTE) } catch (_: Exception) { false }
    }

    private fun copyToClipboard(ctx: Context, text: String) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("p", text))
        } catch (_: Exception) {}
    }
}
