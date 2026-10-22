package com.neurasamu.build.samu_brid.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.neurasamu.build.samu_brid.data.LogRepo

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BridgeAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogRepo.info("AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Phase 2: target-package filter + state machine yahan lagega
    }

    override fun onInterrupt() {
        LogRepo.warn("AccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        LogRepo.info("AccessibilityService destroyed")
        super.onDestroy()
    }

    /** Debug utility — active window ka tree string me dump karta hai. */
    fun dumpTree(): String {
        val root = rootInActiveWindow ?: return "(no active window)"
        val sb = StringBuilder()
        describe(root, 0, sb)
        return sb.toString()
    }

    private fun describe(n: AccessibilityNodeInfo?, depth: Int, sb: StringBuilder) {
        if (n == null || depth > 40) return
        repeat(depth) { sb.append("  ") }
        val r = Rect().also { n.getBoundsInScreen(it) }
        sb.append(n.className ?: "?")
        sb.append(" id=").append(n.viewIdResourceName ?: "-")
        sb.append(" text=").append((n.text ?: n.contentDescription)?.toString() ?: "-")
        sb.append(" bounds=").append(r.toShortString())
        sb.append('\n')
        for (i in 0 until n.childCount) describe(n.getChild(i), depth + 1, sb)
    }
}
