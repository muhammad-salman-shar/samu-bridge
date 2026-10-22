package com.neurasamu.build.samu_brid.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.neurasamu.build.samu_brid.accessibility.BridgeAccessibilityService
import com.neurasamu.build.samu_brid.data.LogRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object BridgeDriver {

    suspend fun sendPrompt(prompt: String, timeoutMs: Long = 60_000L): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val svc = BridgeAccessibilityService.instance
                    ?: error("Accessibility service not connected")
                if (BridgeAccessibilityService.targetPackage.isBlank())
                    error("Target app not selected")

                LogRepo.info("Driver: prompt ${prompt.length} chars")
                AppLauncher.launchApp(svc.applicationContext, BridgeAccessibilityService.targetPackage)
                delay(1200)

                var root: AccessibilityNodeInfo? = null
                for (i in 0 until 10) {
                    root = svc.getTargetRoot(); if (root != null) break; delay(300)
                }
                root ?: error("Target window not found after launch")

                delay(400)
                val baseline = svc.collectAllTexts()
                LogRepo.info("Driver: baseline ${baseline.size} texts")

                val input = svc.findInputNode() ?: error("Input field not found")
                input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                delay(250)

                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, prompt)
                }
                var injected = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (!injected) {
                    LogRepo.warn("SET_TEXT failed, trying clipboard paste")
                    val cm = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("prompt", prompt))
                    delay(200)
                    injected = input.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
                if (!injected) error("Could not inject text")
                delay(500)
                LogRepo.info("Driver: text injected")

                val send = svc.findSendButton() ?: error("Send button not found")
                if (!send.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    error("Send click failed")
                LogRepo.info("Driver: send clicked")

                val reply = svc.waitForResponse(baseline, timeoutMs)
                    ?: error("Timeout — no response in ${timeoutMs}ms")
                LogRepo.info("Driver: response ${reply.length} chars")
                reply
            }.onFailure { LogRepo.error("Driver fail: ${it.message}") }
        }
}
