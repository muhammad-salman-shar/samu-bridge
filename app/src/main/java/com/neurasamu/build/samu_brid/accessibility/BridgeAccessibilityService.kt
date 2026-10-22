package com.neurasamu.build.samu_brid.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.neurasamu.build.samu_brid.data.LogRepo
import java.util.concurrent.ConcurrentHashMap

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: BridgeAccessibilityService? = null
            private set

        @Volatile var targetPackage: String = ""

        private val treeCache = ConcurrentHashMap<String, String>()
        private val lastCaptureAt = ConcurrentHashMap<String, Long>()

        fun getCachedTree(pkg: String): String? = treeCache[pkg]
        fun clearCache() { treeCache.clear(); lastCaptureAt.clear() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogRepo.info("AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (targetPackage.isEmpty() || pkg != targetPackage) return
        val now = System.currentTimeMillis()
        val last = lastCaptureAt[pkg] ?: 0L
        if (now - last < 1000L) return
        val root = rootInActiveWindow ?: return
        lastCaptureAt[pkg] = now
        val sb = StringBuilder()
        describe(root, 0, sb)
        treeCache[pkg] = sb.toString()
    }

    override fun onInterrupt() {
        LogRepo.warn("AccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        LogRepo.info("AccessibilityService destroyed")
        super.onDestroy()
    }

    fun dumpActiveTree(): String {
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
        sb.append(" text=").append((n.text ?: n.contentDescription)?.toString()?.take(120) ?: "-")
        sb.append(" bounds=").append(r.toShortString())
        sb.append(" clickable=").append(n.isClickable)
        sb.append(" editable=").append(n.isEditable)
        sb.append('\n')
        for (i in 0 until n.childCount) describe(n.getChild(i), depth + 1, sb)
    }
}
