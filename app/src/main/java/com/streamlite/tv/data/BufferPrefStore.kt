package com.streamlite.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.bufferPrefs by preferencesDataStore("buffer_prefs")

/**
 * Player buffer level, picked in Settings.
 *
 * - FAST: starts playback almost instantly, small buffer — best for 2GB TV boxes.
 * - BALANCED: quick start with a healthy buffer — the default.
 * - SMOOTH: big buffer for stutter-free playback on slow lines (previous behaviour).
 *
 * [level] is cached in memory at app start (see MainActivity) so PlayerFactory
 * can read it without a suspend call.
 */
object BufferPrefStore {
    const val FAST = "fast"
    const val BALANCED = "balanced"
    const val SMOOTH = "smooth"

    private val KEY = stringPreferencesKey("buffer_level")

    @Volatile
    var level: String = BALANCED
        private set

    suspend fun load(context: Context) {
        level = context.bufferPrefs.data.map { it[KEY] ?: BALANCED }.first()
    }

    suspend fun save(context: Context, value: String) {
        level = value
        context.bufferPrefs.edit { it[KEY] = value }
    }
}
