package com.neurasamu.build.samu_brid.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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
        val sb = StringBuilder(); describe(root, 0, sb); treeCache[pkg] = sb.toString()
    }

    override fun onInterrupt() { LogRepo.warn("AccessibilityService interrupted") }
    override fun onDestroy() { instance = null; LogRepo.info("AccessibilityService destroyed"); super.onDestroy() }

    fun getScreenBounds(): Rect {
        val dm = resources.displayMetrics
        return Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    fun getTargetRoot(): AccessibilityNodeInfo? {
        if (targetPackage.isEmpty()) return rootInActiveWindow
        for (w in windows) {
            val r = w.root ?: continue
            if (r.packageName?.toString() == targetPackage) return r
        }
        val r = rootInActiveWindow
        if (r?.packageName?.toString() == targetPackage) return r
        return null
    }

    /** Flutter-aware: multiple heuristics. */
    fun findInputNode(): AccessibilityNodeInfo? {
        val root = getTargetRoot() ?: return null
        try { root.refresh() } catch (_: Exception) {}

        // 1. TRUE editable (native apps)
        searchTree(root) { it.isEditable }?.let { LogRepo.info("input: editable"); return it }

        // 2. EditText class
        searchTree(root) {
            it.className?.toString()?.contains("EditText", true) == true
        }?.let { LogRepo.info("input: EditText"); return it }

        // 3. Hint text contains "ask" or "message" (DeepSeek placeholder: "Ask anything...")
        searchTree(root) {
            val h = it.hintText?.toString().orEmpty()
            val t = it.text?.toString().orEmpty()
            h.contains("ask", true) || h.contains("message", true) ||
                (t.contains("ask anything", true) && it.isClickable)
        }?.let { LogRepo.info("input: hint matched"); return it }

        // 4. Content description
        searchTree(root) {
            val d = it.contentDescription?.toString().orEmpty()
            (d.contains("message", true) || d.contains("type", true) || d.contains("ask", true)) &&
                (it.isClickable || it.isFocusable)
        }?.let { LogRepo.info("input: desc matched"); return it }

        // 5. Bottom-half focusable+clickable View (Flutter signature)
        val screen = getScreenBounds()
        searchTree(root) { n ->
            if (!n.isFocusable && !n.isClickable) return@searchTree false
            val b = Rect(); n.getBoundsInScreen(b)
            b.centerY() > screen.height() * 0.6 && b.width() > 200 && b.height() > 40
        }?.let { LogRepo.info("input: bottom-half view"); return it }

        return null
    }

    fun findSendButton(): AccessibilityNodeInfo? {
        val root = getTargetRoot() ?: return null
        searchTree(root) {
            it.isClickable && (
                it.contentDescription?.contains("send", true) == true ||
                it.text?.toString()?.contains("send", true) == true
            )
        }?.let { return it }
        val input = findInputNode() ?: return null
        val ib = Rect().also { input.getBoundsInScreen(it) }
        return searchTree(root) {
            if (!it.isClickable) return@searchTree false
            val b = Rect().also { r -> it.getBoundsInScreen(r) }
            b.centerY() in (ib.centerY() - 80)..(ib.centerY() + 80) &&
                b.left >= ib.right - 20 && b.left <= ib.right + 250 &&
                b.width() in 30..250 && b.height() in 30..250
        }
    }

    fun collectAllTexts(): Set<String> {
        val out = mutableSetOf<String>()
        val root = getTargetRoot() ?: return out
        walkTree(root) { n ->
            val t = n.text?.toString()?.trim() ?: return@walkTree
            if (t.isNotBlank() && t.length > 3) out.add(t)
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
            val cand = fresh.filter { it.length > 12 }.maxByOrNull { it.length }
            if (cand != null && cand != best) { best = cand; lastChange = System.currentTimeMillis() }
            if (best != null && System.currentTimeMillis() - lastChange >= stableMs) return best
        }
        return best
    }

    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        return dispatchGesture(g, null, null)
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
            searchTree(c, p)?.let { return it }
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
        sb.append(" hint=").append(n.hintText?.toString()?.take(40) ?: "-")
        sb.append(" b=").append(r.toShortString())
        sb.append(" clk=").append(n.isClickable)
        sb.append(" ed=").append(n.isEditable)
        sb.append(" foc=").append(n.isFocusable)
        sb.append('\n')
        for (i in 0 until n.childCount) describe(n.getChild(i), d + 1, sb)
    }
}
