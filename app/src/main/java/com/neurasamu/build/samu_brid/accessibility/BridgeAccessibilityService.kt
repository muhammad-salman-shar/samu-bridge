package com.neurasamu.build.samu_brid.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.neurasamu.build.samu_brid.data.LogRepo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: BridgeAccessibilityService? = null
            private set
        @Volatile var targetPackage: String = ""
        private val treeCache = ConcurrentHashMap<String, String>()
        private val lastCaptureAt = ConcurrentHashMap<String, Long>()
        fun getCachedTree(pkg: String): String? = treeCache[pkg]

        @Volatile var lastTargetFrame: Bitmap? = null
            private set
        @Volatile var lastTargetFrameAt: Long = 0L
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        LogRepo.info("AccessibilityService connected")
        startCaptureLoop()
    }

    private var captureThread: Thread? = null
    private fun startCaptureLoop() {
        if (captureThread != null) return
        captureThread = Thread {
            while (instance != null) {
                try {
                    if (targetPackage.isNotBlank() && getTargetRoot() != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val bmp = kotlinx.coroutines.runBlocking { takeScreenshotBitmap() }
                        if (bmp != null) {
                            val cropped = cropToTarget(bmp)
                            if (cropped != null) {
                                lastTargetFrame = cropped
                                lastTargetFrameAt = System.currentTimeMillis()
                            }
                        }
                    }
                } catch (_: Throwable) {}
                try { Thread.sleep(2000) } catch (_: InterruptedException) { break }
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun cropToTarget(full: Bitmap): Bitmap? {
        val root = getTargetRoot() ?: return null
        val b = Rect(); root.getBoundsInScreen(b)
        if (b.width() <= 0 || b.height() <= 0) return full
        val x = b.left.coerceIn(0, (full.width - 1).coerceAtLeast(0))
        val y = b.top.coerceIn(0, (full.height - 1).coerceAtLeast(0))
        val w = (b.width()).coerceAtMost(full.width - x)
        val h = (b.height()).coerceAtMost(full.height - y)
        if (w <= 0 || h <= 0) return null
        return try { Bitmap.createBitmap(full, x, y, w, h) } catch (_: Throwable) { null }
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
    override fun onDestroy() {
        instance = null
        captureThread?.interrupt()
        captureThread = null
        LogRepo.info("AccessibilityService destroyed")
        super.onDestroy()
    }

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

    fun getFocusedInput(): AccessibilityNodeInfo? {
        val root = getTargetRoot() ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
    }

    fun findInputNode(): AccessibilityNodeInfo? {
        val root = getTargetRoot() ?: return null
        try { root.refresh() } catch (_: Exception) {}

        searchTree(root) { it.isEditable }?.let { return it }
        searchTree(root) { it.className?.toString()?.contains("EditText", true) == true }?.let { return it }

        searchTree(root) {
            val h = it.hintText?.toString().orEmpty()
            val t = it.text?.toString().orEmpty()
            h.contains("ask", true) || h.contains("message", true) ||
                (t.contains("ask anything", true) && (it.isClickable || it.isFocusable))
        }?.let { return it }

        searchTree(root) {
            val d = it.contentDescription?.toString().orEmpty()
            (d.contains("message", true) || d.contains("type", true) || d.contains("ask", true)) &&
                (it.isClickable || it.isFocusable)
        }?.let { return it }

        val screen = getScreenBounds()
        return searchTree(root) { n ->
            if (!n.isFocusable && !n.isClickable) return@searchTree false
            val b = Rect(); n.getBoundsInScreen(b)
            b.centerY() > screen.height() * 0.55 && b.width() > 200 && b.height() > 40
        }
    }

    /** Deepest descendant that looks editable, walking from candidate. */
    fun findEditableIn(candidate: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (candidate == null) return null
        try { candidate.refresh() } catch (_: Exception) {}
        if (candidate.isEditable) return candidate
        return searchTree(candidate) { it.isEditable || it.className?.toString()?.contains("EditText", true) == true }
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

    fun findPasteMenuItem(): AccessibilityNodeInfo? {
        val root = getTargetRoot() ?: return null
        return searchTree(root) {
            val t = it.text?.toString().orEmpty()
            val d = it.contentDescription?.toString().orEmpty()
            (t.equals("paste", true) || t.contains("Paste", true) || d.contains("paste", true)) && it.isClickable
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

    fun longPressAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 600L))
            .build()
        return dispatchGesture(g, null, null)
    }

    fun tapNode(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        val b = Rect(); n.getBoundsInScreen(b)
        if (b.width() <= 0 || b.height() <= 0) return false
        return tapAt(b.centerX().toFloat(), b.centerY().toFloat())
    }

    /** Android 11+ screen capture. Returns null on < R or error. */
    suspend fun takeScreenshotBitmap(): Bitmap? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                cont.resume(null); return@suspendCancellableCoroutine
            }
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    ContextCompat.getMainExecutor(this),
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            val bmp = try {
                                val hb: HardwareBuffer = result.hardwareBuffer
                                val cs = result.colorSpace
                                Bitmap.wrapHardwareBuffer(hb, cs)?.copy(Bitmap.Config.ARGB_8888, false)
                            } catch (_: Throwable) { null } finally {
                                try { result.hardwareBuffer.close() } catch (_: Throwable) {}
                            }
                            cont.resume(bmp)
                        }
                        override fun onFailure(errorCode: Int) {
                            LogRepo.warn("Screenshot failed: $errorCode")
                            cont.resume(null)
                        }
                    }
                )
            } catch (t: Throwable) {
                LogRepo.warn("Screenshot exception: ${t.message}")
                cont.resume(null)
            }
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
