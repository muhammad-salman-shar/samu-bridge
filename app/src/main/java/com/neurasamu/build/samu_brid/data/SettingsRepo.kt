package com.neurasamu.build.samu_brid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds: DataStore<Preferences> by preferencesDataStore("samu_bridge_settings")

data class BridgeSettings(
    val websiteUrl: String = "",
    val modelName: String = "samu",
    val apiToken: String = "samu123456",
    val port: Int = 8787,
    val targetPackage: String = "com.deepseek.chat",
    val targetLabel: String = "DeepSeek",
    val apkPath: String = ""
)

class SettingsRepo(private val ctx: Context) {
    private val kUrl = stringPreferencesKey("website_url")
    private val kModel = stringPreferencesKey("model_name")
    private val kToken = stringPreferencesKey("api_token")
    private val kPort = intPreferencesKey("port")
    private val kPkg = stringPreferencesKey("target_package")
    private val kLabel = stringPreferencesKey("target_label")
    private val kApk = stringPreferencesKey("apk_path")

    val flow: Flow<BridgeSettings> = ctx.ds.data.map { p ->
        BridgeSettings(
            websiteUrl = p[kUrl] ?: "",
            modelName = p[kModel] ?: "samu",
            apiToken = p[kToken] ?: "samu123456",
            port = p[kPort] ?: 8787,
            targetPackage = (p[kPkg] ?: "").ifBlank { "com.deepseek.chat" },
            targetLabel = (p[kLabel] ?: "").ifBlank { "DeepSeek" },
            apkPath = p[kApk] ?: ""
        )
    }

    suspend fun save(s: BridgeSettings) {
        ctx.ds.edit {
            it[kUrl] = s.websiteUrl
            it[kModel] = s.modelName
            it[kToken] = s.apiToken
            it[kPort] = s.port
            it[kPkg] = s.targetPackage
            it[kLabel] = s.targetLabel
            it[kApk] = s.apkPath
        }
    }
}
