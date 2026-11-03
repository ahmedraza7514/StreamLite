package com.streamlite.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.favPrefs by preferencesDataStore("fav_prefs")

/** One favorite item. [url] is the resolved playback URL (unique key). */
data class FavItem(
    val url: String,
    val title: String,
    val kind: String, // "live" | "vod" | "episode"
    val logo: String = "",
)

/**
 * Favorites storage (app-wide). Backed by DataStore as a JSON array.
 * Toggled from the player bar's star button; listed on the Favorites screen.
 */
object FavStore {
    private val KEY = stringPreferencesKey("favorites_json")

    private fun parse(raw: String?): List<FavItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                FavItem(
                    url = o.optString("url"),
                    title = o.optString("title"),
                    kind = o.optString("kind", "live"),
                    logo = o.optString("logo"),
                )
            }.filter { it.url.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serialize(items: List<FavItem>): String {
        val arr = JSONArray()
        items.forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("url", f.url)
                    put("title", f.title)
                    put("kind", f.kind)
                    put("logo", f.logo)
                },
            )
        }
        return arr.toString()
    }

    fun flow(context: Context): Flow<List<FavItem>> =
        context.favPrefs.data.map { parse(it[KEY]) }

    suspend fun getAll(context: Context): List<FavItem> = flow(context).first()

    suspend fun isFav(context: Context, url: String): Boolean =
        getAll(context).any { it.url == url }

    /** Toggles the item. Returns the new state: true = now a favorite. */
    suspend fun toggle(context: Context, item: FavItem): Boolean {
        val current = getAll(context).toMutableList()
        val existing = current.indexOfFirst { it.url == item.url }
        val nowFav = if (existing >= 0) {
            current.removeAt(existing)
            false
        } else {
            current.add(0, item)
            true
        }
        context.favPrefs.edit { it[KEY] = serialize(current) }
        return nowFav
    }

    suspend fun remove(context: Context, url: String) {
        val current = getAll(context).filter { it.url != url }
        context.favPrefs.edit { it[KEY] = serialize(current) }
    }
}
