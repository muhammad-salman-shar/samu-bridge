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

                // Wait for root
                var root: AccessibilityNodeInfo? = null
                for (i in 0 until 12) {
                    root = svc.getTargetRoot(); if (root != null) break; delay(300)
                }
                root ?: run {
                    val d = svc.dumpActiveTree()
                    LogRepo.error("Root missing. Tree:\n${d.take(1500)}")
                    error("Target window not found")
                }

                delay(500)

                // Try finding input directly (native/heuristic)
                var input = svc.findInputNode()

                // Flutter fallback: coordinate tap bottom-center, then retry
                if (input == null) {
                    LogRepo.warn("No input — trying coordinate taps (Flutter fallback)")
                    val b = svc.getScreenBounds()
                    svc.tapAt(b.width() * 0.4f, b.height() * 0.88f); delay(900)
                    input = svc.findInputNode()
                    if (input == null) {
                        svc.tapAt(b.width() * 0.4f, b.height() * 0.93f); delay(900)
                        input = svc.findInputNode()
                    }
                }

                input ?: run {
                    val d = svc.dumpActiveTree()
                    LogRepo.error("Input still missing. Tree:\n${d.take(2500)}")
                    error("Input field not found")
                }

                // Capture baseline AFTER tap (chat may have changed)
                val baseline = svc.collectAllTexts()
                LogRepo.info("Driver: baseline ${baseline.size} texts")

                // Inject text
                input.performAction(AccessibilityNodeInfo.ACTION_FOCUS); delay(200)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, prompt)
                }
                var injected = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (!injected) {
                    LogRepo.warn("SET_TEXT failed — clipboard paste fallback")
                    val cm = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("p", prompt)); delay(250)
                    injected = input.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
                if (!injected) error("Text injection failed")
                LogRepo.info("Driver: text injected")
                delay(600)

                // Send: try IME enter → button → coordinate
                var sent = false
                try { sent = input.performAction(AccessibilityNodeInfo.ACTION_CLICK); } catch (_: Exception) {}
                // Some apps need separate send
                val sendBtn = svc.findSendButton()
                if (sendBtn != null && sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    sent = true; LogRepo.info("Driver: send via button")
                }
                if (!sent) {
                    // Fallback: tap right of input field
                    val ib = Rect(); input.getBoundsInScreen(ib)
                    svc.tapAt(ib.right - 30f, ib.centerY().toFloat())
                    LogRepo.info("Driver: send via coordinate tap (${ib.right - 30}, ${ib.centerY()})")
                }
                delay(800)

                // Post-send baseline (user message now visible)
                val post = svc.collectAllTexts()

                // Wait for response
                val reply = svc.waitForResponse(post, timeoutMs)
                    ?: error("Timeout — no response in ${timeoutMs}ms")
                LogRepo.info("Driver: response ${reply.length} chars")
                reply
            }.onFailure { LogRepo.error("Driver fail: ${it.message}") }
        }
}
