package com.streamlite.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class LoginType { XTREAM, M3U }

data class LoginSession(
    val type: LoginType,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
)

private val Context.dataStore by preferencesDataStore("streamlite_prefs")

/** Persists the login on-device so the app opens straight into the home screen. */
class SessionManager(private val context: Context) {

    private object Keys {
        val TYPE = stringPreferencesKey("login_type")
        val SERVER = stringPreferencesKey("server")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val M3U_URL = stringPreferencesKey("m3u_url")
    }

    val sessionFlow: Flow<LoginSession?> = context.dataStore.data.map { p ->
        when (p[Keys.TYPE]) {
            "xtream" -> LoginSession(
                type = LoginType.XTREAM,
                server = p[Keys.SERVER] ?: "",
                username = p[Keys.USERNAME] ?: "",
                password = p[Keys.PASSWORD] ?: "",
            )
            "m3u" -> LoginSession(
                type = LoginType.M3U,
                m3uUrl = p[Keys.M3U_URL] ?: "",
            )
            else -> null
        }
    }

    suspend fun getOnce(): LoginSession? = sessionFlow.first()

    suspend fun saveXtream(server: String, username: String, password: String) {
        context.dataStore.edit { p ->
            p[Keys.TYPE] = "xtream"
            p[Keys.SERVER] = server.trim().trimEnd('/')
            p[Keys.USERNAME] = username.trim()
            p[Keys.PASSWORD] = password.trim()
            p.remove(Keys.M3U_URL)
        }
    }

    suspend fun saveM3u(url: String) {
        context.dataStore.edit { p ->
            p[Keys.TYPE] = "m3u"
            p[Keys.M3U_URL] = url.trim()
            p.remove(Keys.SERVER)
            p.remove(Keys.USERNAME)
            p.remove(Keys.PASSWORD)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
