package com.off.audioconverter.ui

import android.content.Context

/**
 * Small SharedPreferences-backed store so editor and settings choices survive
 * navigating away and restarting the app.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("off_settings", Context.MODE_PRIVATE)

    var bitrate: Int
        get() = prefs.getInt(KEY_BITRATE, 192)
        set(value) = prefs.edit().putInt(KEY_BITRATE, value).apply()

    var speed: Float
        get() = prefs.getFloat(KEY_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_SPEED, value).apply()

    var volume: Float
        get() = prefs.getFloat(KEY_VOLUME, 1f)
        set(value) = prefs.edit().putFloat(KEY_VOLUME, value).apply()

    var fadeIn: Float
        get() = prefs.getFloat(KEY_FADE_IN, 0f)
        set(value) = prefs.edit().putFloat(KEY_FADE_IN, value).apply()

    var fadeOut: Float
        get() = prefs.getFloat(KEY_FADE_OUT, 0f)
        set(value) = prefs.edit().putFloat(KEY_FADE_OUT, value).apply()

    var reducedMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCED_MOTION, false)
        set(value) = prefs.edit().putBoolean(KEY_REDUCED_MOTION, value).apply()

    var saavnEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAAVN, true)
        set(value) = prefs.edit().putBoolean(KEY_SAAVN, value).apply()

    var youtubeEnabled: Boolean
        get() = prefs.getBoolean(KEY_YOUTUBE, true)
        set(value) = prefs.edit().putBoolean(KEY_YOUTUBE, value).apply()

    var lastTab: Int
        get() = prefs.getInt(KEY_TAB, 0)
        set(value) = prefs.edit().putInt(KEY_TAB, value).apply()

    fun recentSearches(): List<String> =
        prefs.getString(KEY_RECENT, "").orEmpty()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun addRecentSearch(query: String) {
        val term = query.trim()
        if (term.length < 2) return
        val updated = (listOf(term) + recentSearches().filterNot { it.equals(term, ignoreCase = true) })
            .take(8)
        prefs.edit().putString(KEY_RECENT, updated.joinToString("\n")).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    private companion object {
        const val KEY_BITRATE = "bitrate"
        const val KEY_SPEED = "speed"
        const val KEY_VOLUME = "volume"
        const val KEY_FADE_IN = "fade_in"
        const val KEY_FADE_OUT = "fade_out"
        const val KEY_REDUCED_MOTION = "reduced_motion"
        const val KEY_SAAVN = "source_saavn"
        const val KEY_YOUTUBE = "source_youtube"
        const val KEY_TAB = "last_tab"
        const val KEY_RECENT = "recent_searches"
    }
}
