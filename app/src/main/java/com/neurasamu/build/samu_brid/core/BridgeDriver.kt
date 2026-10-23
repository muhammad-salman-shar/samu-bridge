package com.neurasamu.build.samu_brid.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.neurasamu.build.samu_brid.accessibility.BridgeAccessibilityService
import com.neurasamu.build.samu_brid.data.LogRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object BridgeDriver {

    const val DEFAULT_PACKAGE = "com.deepseek.chat"

    // Normalized 0..1 taps (user-supplied 0..1000 scale / 1000)
    private const val INPUT_NORM_X = 0.592f
    private const val INPUT_NORM_Y = 0.922f
    private const val SEND_NORM_X = 0.940f
    private const val SEND_NORM_Y = 0.922f

    suspend fun sendPrompt(prompt: String, timeoutMs: Long = 180_000L): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val svc = BridgeAccessibilityService.instance
                    ?: error("Accessibility service not connected")

                val pkg = BridgeAccessibilityService.targetPackage.ifBlank { DEFAULT_PACKAGE }
                BridgeAccessibilityService.targetPackage = pkg
                LogRepo.info("Driver: target=$pkg, prompt=${prompt.length} chars")

                // No auto-launch — user keeps DeepSeek open in background
                var root = svc.getTargetRoot()
                if (root == null) { delay(600); root = svc.getTargetRoot() }
                if (root == null) error("DeepSeek window not found — open DeepSeek app first, then return here")

                val baseline = svc.collectAllTexts()
                LogRepo.info("Driver: baseline ${baseline.size} texts")

                if (!injectText(svc, prompt)) error("Text injection failed")
                LogRepo.info("Driver: text injected")
                delay(500)

                if (!clickSend(svc)) error("Send click failed")
                LogRepo.info("Driver: send dispatched")

                delay(900)
                val post = svc.collectAllTexts()
                val reply = svc.waitForResponse(post, timeoutMs)
                    ?: error("Timeout — no response in ${timeoutMs}ms")
                LogRepo.info("Driver: response ${reply.length} chars")
                reply
            }.onFailure { LogRepo.error("Driver fail: ${it.message}") }
        }

    private suspend fun injectText(svc: BridgeAccessibilityService, prompt: String): Boolean {
        // Path A: tree-based
        svc.findInputNode()?.let { inp ->
            svc.findEditableIn(inp)?.let { e ->
                if (trySetText(e, prompt)) { LogRepo.info("inject: tree editable"); return true }
            }
            if (trySetText(inp, prompt)) { LogRepo.info("inject: tree wrapper"); return true }
        }

        // Path B: coordinate tap → focused SET_TEXT / PASTE
        val b = svc.getScreenBounds()
        val tx = b.width() * INPUT_NORM_X
        val ty = b.height() * INPUT_NORM_Y
        LogRepo.info("inject: coord tap input (${tx.toInt()}, ${ty.toInt()})")
        svc.tapAt(tx, ty)
        delay(850)

        svc.getFocusedInput()?.let { f ->
            if (trySetText(f, prompt)) { LogRepo.info("inject: focused SET_TEXT"); return true }
            if (tryPaste(f, svc, prompt)) { LogRepo.info("inject: focused PASTE"); return true }
        }

        // Path C: re-find after focus
        delay(400)
        svc.findInputNode()?.let { n ->
            svc.findEditableIn(n)?.let { e ->
                if (trySetText(e, prompt)) { LogRepo.info("inject: refind"); return true }
            }
        }
        LogRepo.warn("inject: all strategies failed")
        return false
    }

    private fun clickSend(svc: BridgeAccessibilityService): Boolean {
        // A: tree button (by desc / text / position)
        svc.findSendButton()?.let { btn ->
            if (btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                LogRepo.info("send: tree button"); return true
            }
        }
        // B: IME enter on focused
        svc.getFocusedInput()?.let { f ->
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    if (f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) {
                        LogRepo.info("send: IME enter"); return true
                    }
                } catch (_: Exception) {}
            }
        }
        // C: tap right-of-input
        val inp = svc.findInputNode() ?: svc.getFocusedInput()
        if (inp != null) {
            val ib = Rect(); inp.getBoundsInScreen(ib)
            if (ib.width() > 0) {
                svc.tapAt(ib.right + 40f, ib.centerY().toFloat())
                LogRepo.info("send: right-of-input tap (${ib.right + 40}, ${ib.centerY()})")
                return true
            }
        }
        // D: normalized coordinate tap (last resort)
        val b = svc.getScreenBounds()
        val sx = b.width() * SEND_NORM_X
        val sy = b.height() * SEND_NORM_Y
        svc.tapAt(sx, sy)
        LogRepo.info("send: coord tap (${sx.toInt()}, ${sy.toInt()})")
        return true
    }

    private fun trySetText(n: AccessibilityNodeInfo, text: String): Boolean {
        try { n.refresh() } catch (_: Exception) {}
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return try { n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) } catch (_: Exception) { false }
    }

    private fun tryPaste(n: AccessibilityNodeInfo, svc: BridgeAccessibilityService, text: String): Boolean {
        try {
            val cm = svc.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("p", text))
        } catch (_: Exception) {}
        return try { n.performAction(AccessibilityNodeInfo.ACTION_PASTE) } catch (_: Exception) { false }
    }
}
