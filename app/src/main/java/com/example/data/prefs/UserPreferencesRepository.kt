package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.ProxyQuality
import com.example.core.model.ThemeIntensity
import com.example.core.model.UserPreferences
import com.example.core.model.WorkspacePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.excevoDataStore: DataStore<Preferences> by preferencesDataStore(name = "excevo_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_INTENSITY = stringPreferencesKey("theme_intensity")
        val WORKSPACE_PRESET = stringPreferencesKey("workspace_preset")
        val PROXY_QUALITY = stringPreferencesKey("proxy_quality")
        val HARDWARE_DECODE = booleanPreferencesKey("hardware_decode")
        val TIMELINE_SNAP = booleanPreferencesKey("timeline_snap")
        val SCRUB_CACHE_LIMIT_MB = intPreferencesKey("scrub_cache_limit_mb")
        val AI_USAGE_COUNT = intPreferencesKey("ai_usage_count")
    }

    val preferences: Flow<UserPreferences> = context.excevoDataStore.data.map { prefs ->
        UserPreferences(
            themeIntensity = runCatching {
                ThemeIntensity.valueOf(prefs[Keys.THEME_INTENSITY] ?: ThemeIntensity.STUDIO_DARK.name)
            }.getOrDefault(ThemeIntensity.STUDIO_DARK),
            workspacePreset = WorkspacePreset.fromId(prefs[Keys.WORKSPACE_PRESET]),
            proxyQuality = runCatching {
                ProxyQuality.valueOf(prefs[Keys.PROXY_QUALITY] ?: ProxyQuality.PROXY_720P.name)
            }.getOrDefault(ProxyQuality.PROXY_720P),
            hardwareDecodeEnabled = prefs[Keys.HARDWARE_DECODE] ?: true,
            timelineSnapEnabled = prefs[Keys.TIMELINE_SNAP] ?: true,
            scrubCacheLimitMb = prefs[Keys.SCRUB_CACHE_LIMIT_MB] ?: 512,
            aiUsageCount = prefs[Keys.AI_USAGE_COUNT] ?: 0,
        )
    }

    suspend fun setThemeIntensity(intensity: ThemeIntensity) {
        context.excevoDataStore.edit { it[Keys.THEME_INTENSITY] = intensity.name }
    }

    suspend fun setWorkspacePreset(preset: WorkspacePreset) {
        context.excevoDataStore.edit { it[Keys.WORKSPACE_PRESET] = preset.id }
    }

    suspend fun setProxyQuality(quality: ProxyQuality) {
        context.excevoDataStore.edit { it[Keys.PROXY_QUALITY] = quality.name }
    }

    suspend fun setHardwareDecode(enabled: Boolean) {
        context.excevoDataStore.edit { it[Keys.HARDWARE_DECODE] = enabled }
    }

    suspend fun setTimelineSnap(enabled: Boolean) {
        context.excevoDataStore.edit { it[Keys.TIMELINE_SNAP] = enabled }
    }

    suspend fun setScrubCacheLimitMb(limitMb: Int) {
        context.excevoDataStore.edit { it[Keys.SCRUB_CACHE_LIMIT_MB] = limitMb.coerceIn(128, 2048) }
    }

    suspend fun incrementAiUsage() {
        context.excevoDataStore.edit { prefs ->
            prefs[Keys.AI_USAGE_COUNT] = (prefs[Keys.AI_USAGE_COUNT] ?: 0) + 1
        }
    }
}
