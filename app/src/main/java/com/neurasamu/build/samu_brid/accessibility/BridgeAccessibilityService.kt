package com.neurasamu.build.samu_brid.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.neurasamu.build.samu_brid.data.LogRepo
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: BridgeAccessibilityService? = null
            private set

        @Volatile var targetPackage: String = ""

        private val treeCache = ConcurrentHashMap<String, String>()
        private val lastCaptureAt = ConcurrentHashMap<String, Long>()

        fun getCachedTree(pkg: String): String? = treeCache[pkg]
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
        if (now - (lastCaptureAt[pkg] ?: 0L) < 1000L) return
        val root = rootInActiveWindow ?: return
        lastCaptureAt[pkg] = now
        val sb = StringBuilder()
        describe(root, 0, sb)
        treeCache[pkg] = sb.toString()
    }

    override fun onInterrupt() { LogRepo.warn("AccessibilityService interrupted") }

    override fun onDestroy() {
        instance = null
        LogRepo.info("AccessibilityService destroyed")
        super.onDestroy()
    }

    /** Target app ka root — foreground ho ya na ho. */
    fun getTargetRoot(): AccessibilityNodeInfo? {
        if (targetPackage.isEmpty()) return rootInActiveWindow
        for (w in windows) {
            val r = w.root ?: continue
            if (r.packageName?.toString() == targetPackage) return r
        }
        return null
    }

    fun findInputNode(): AccessibilityNodeInfo? {
        val root = getTargetRoot() ?: return null
        return searchTree(root) {
            it.isEditable || it.className?.toString()?.contains("EditText", true) == true
        }
    }

    fun findSendButton(): AccessibilityNodeInfo? {
        val root = getTargetRoot() ?: return null
        val byDesc = searchTree(root) {
            it.isClickable && (
                it.contentDescription?.contains("send", true) == true ||
                it.text?.toString()?.contains("send", true) == true
            )
        }
        if (byDesc != null) return byDesc
        val input = findInputNode() ?: return null
        val ib = Rect().also { input.getBoundsInScreen(it) }
        return searchTree(root) {
            if (!it.isClickable) return@searchTree false
            val b = Rect().also { r -> it.getBoundsInScreen(r) }
            b.left >= ib.right - 80 &&
                abs(b.centerY() - ib.centerY()) < 150 &&
                b.width() in 30..200 && b.height() in 30..200
        }
    }

    fun collectAllTexts(): Set<String> {
        val out = mutableSetOf<String>()
        val root = getTargetRoot() ?: return out
        walkTree(root) { n ->
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrBlank() && t.length > 5) out.add(t)
        }
        return out
    }

    fun waitForResponse(before: Set<String>, timeoutMs: Long, stableMs: Long = 2500L): String? {
        val start = System.currentTimeMillis()
        var best: String? = null
        var lastChange = start
        while (System.currentTimeMillis() - start < timeoutMs) {
            try { Thread.sleep(500) } catch (_: InterruptedException) { return best }
            val fresh = collectAllTexts() - before
            val cand = fresh.filter { it.length > 15 }.maxByOrNull { it.length }
            if (cand != null && cand != best) { best = cand; lastChange = System.currentTimeMillis() }
            if (best != null && System.currentTimeMillis() - lastChange >= stableMs) return best
        }
        return best
    }

    fun dumpActiveTree(): String {
        val root = getTargetRoot() ?: rootInActiveWindow ?: return "(no active window)"
        val sb = StringBuilder(); describe(root, 0, sb); return sb.toString()
    }

    private fun walkTree(n: AccessibilityNodeInfo?, cb: (AccessibilityNodeInfo) -> Unit) {
        if (n == null) return
        cb(n)
        for (i in 0 until n.childCount) walkTree(n.getChild(i), cb)
    }

    private fun searchTree(n: AccessibilityNodeInfo?, p: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (p(n)) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val r = searchTree(c, p); if (r != null) return r
        }
        return null
    }

    private fun describe(n: AccessibilityNodeInfo?, d: Int, sb: StringBuilder) {
        if (n == null || d > 40) return
        repeat(d) { sb.append("  ") }
        val r = Rect().also { n.getBoundsInScreen(it) }
        sb.append(n.className ?: "?")
        sb.append(" id=").append(n.viewIdResourceName ?: "-")
        sb.append(" text=").append((n.text ?: n.contentDescription)?.toString()?.take(120) ?: "-")
        sb.append(" b=").append(r.toShortString())
        sb.append(" clk=").append(n.isClickable)
        sb.append(" ed=").append(n.isEditable)
        sb.append('\n')
        for (i in 0 until n.childCount) describe(n.getChild(i), d + 1, sb)
    }
}
