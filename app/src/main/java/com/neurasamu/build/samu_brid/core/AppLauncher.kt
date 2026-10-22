package com.neurasamu.build.samu_brid.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.neurasamu.build.samu_brid.data.LogRepo
import java.io.File

data class InstalledApp(val label: String, val packageName: String)

object AppLauncher {

    fun listInstalled(ctx: Context): List<InstalledApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launchApp(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: run {
            LogRepo.warn("No launch intent for $pkg")
            return false
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        LogRepo.info("Launched $pkg")
        return true
    }

    fun openPlayStore(ctx: Context, pkg: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(market) }.onFailure {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        LogRepo.info("Play Store → $pkg")
    }

    fun installApk(ctx: Context, apkPath: String): Result<Unit> = runCatching {
        val f = File(apkPath)
        require(f.exists()) { "APK not found: $apkPath" }
        require(apkPath.endsWith(".apk", ignoreCase = true)) { "Not an APK: $apkPath" }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(i)
    }

    fun openAccessibilitySettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun isAccessibilityEnabled(): Boolean =
        com.neurasamu.build.samu_brid.accessibility.BridgeAccessibilityService.instance != null
}
