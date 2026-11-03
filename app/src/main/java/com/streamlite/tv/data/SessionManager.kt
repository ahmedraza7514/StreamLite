package com.streamlite.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

enum class LoginType { XTREAM, M3U }

data class LoginSession(
    val type: LoginType,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
) {
    /** Short label shown in the account switcher. */
    fun label(): String = when (type) {
        LoginType.XTREAM -> "$username @ $server"
        LoginType.M3U -> "M3U playlist"
    }
}

private val Context.dataStore by preferencesDataStore("streamlite_prefs")

/**
 * Persists logins on-device. Supports MULTIPLE accounts: the user can add
 * several Xtream/M3U logins and switch between them from the Account screen.
 *
 * Backward compatible: a login saved by the old single-account version is
 * migrated into the accounts list on first access.
 */
class SessionManager(private val context: Context) {

    private object Keys {
        // New multi-account storage
        val ACCOUNTS = stringPreferencesKey("accounts_json")
        val ACTIVE = intPreferencesKey("active_index")
        // Old single-account keys (migrated once, then removed)
        val TYPE = stringPreferencesKey("login_type")
        val SERVER = stringPreferencesKey("server")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val M3U_URL = stringPreferencesKey("m3u_url")
    }

    private fun LoginSession.toJson(): String = JSONObject().apply {
        put("type", if (type == LoginType.M3U) "m3u" else "xtream")
        put("server", server)
        put("username", username)
        put("password", password)
        put("m3uUrl", m3uUrl)
    }.toString()

    private fun String.toSession(): LoginSession? = try {
        val o = JSONObject(this)
        LoginSession(
            type = if (o.optString("type") == "m3u") LoginType.M3U else LoginType.XTREAM,
            server = o.optString("server"),
            username = o.optString("username"),
            password = o.optString("password"),
            m3uUrl = o.optString("m3uUrl"),
        )
    } catch (_: Exception) {
        null
    }

    private fun sameAccount(a: LoginSession, b: LoginSession): Boolean = when {
        a.type != b.type -> false
        a.type == LoginType.M3U -> a.m3uUrl == b.m3uUrl
        else -> a.server == b.server && a.username == b.username
    }

    /** One-time migration from the old single-account keys. */
    suspend fun migrateIfNeeded() {
        val prefs = context.dataStore.data.first()
        if (prefs[Keys.ACCOUNTS] != null) {
            // Already migrated — just drop any leftover old keys.
            if (prefs[Keys.TYPE] != null) {
                context.dataStore.edit {
                    it.remove(Keys.TYPE)
                    it.remove(Keys.SERVER)
                    it.remove(Keys.USERNAME)
                    it.remove(Keys.PASSWORD)
                    it.remove(Keys.M3U_URL)
                }
            }
            return
        }
        val old = when (prefs[Keys.TYPE]) {
            "xtream" -> LoginSession(
                type = LoginType.XTREAM,
                server = prefs[Keys.SERVER] ?: "",
                username = prefs[Keys.USERNAME] ?: "",
                password = prefs[Keys.PASSWORD] ?: "",
            )
            "m3u" -> LoginSession(
                type = LoginType.M3U,
                m3uUrl = prefs[Keys.M3U_URL] ?: "",
            )
            else -> null
        }
        context.dataStore.edit { p ->
            if (old != null) {
                val arr = JSONArray().apply { put(old.toJson()) }
                p[Keys.ACCOUNTS] = arr.toString()
                p[Keys.ACTIVE] = 0
            } else {
                p[Keys.ACCOUNTS] = JSONArray().toString()
                p[Keys.ACTIVE] = 0
            }
            p.remove(Keys.TYPE)
            p.remove(Keys.SERVER)
            p.remove(Keys.USERNAME)
            p.remove(Keys.PASSWORD)
            p.remove(Keys.M3U_URL)
        }
    }

    private fun parseAccounts(raw: String?): List<LoginSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getString(i).toSession() }.filterNotNull()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serialize(accounts: List<LoginSession>): String =
        JSONArray().apply { accounts.forEach { put(it.toJson()) } }.toString()

    fun accountsFlow(): Flow<List<LoginSession>> =
        context.dataStore.data.map { parseAccounts(it[Keys.ACCOUNTS]) }

    suspend fun getAccounts(): List<LoginSession> {
        migrateIfNeeded()
        return accountsFlow().first()
    }

    private suspend fun activeIndex(): Int {
        val n = getAccounts().size
        if (n == 0) return 0
        val i = context.dataStore.data.map { it[Keys.ACTIVE] ?: 0 }.first()
        return i.coerceIn(0, n - 1)
    }

    /** The currently active session, or null when no account exists. */
    suspend fun getActive(): LoginSession? {
        val accounts = getAccounts()
        if (accounts.isEmpty()) return null
        return accounts[activeIndex()]
    }

    /** Old single-session accessor — now returns the active account. */
    suspend fun getOnce(): LoginSession? = getActive()

    private suspend fun saveAll(accounts: List<LoginSession>, active: Int) {
        context.dataStore.edit { p ->
            p[Keys.ACCOUNTS] = serialize(accounts)
            p[Keys.ACTIVE] = if (accounts.isEmpty()) 0 else active.coerceIn(0, accounts.size - 1)
        }
    }

    /** Save (add or replace) and make it the active account. */
    suspend fun saveAccount(s: LoginSession) {
        val list = getAccounts().toMutableList()
        val existing = list.indexOfFirst { sameAccount(it, s) }
        if (existing >= 0) list[existing] = s else list.add(s)
        val idx = list.indexOfFirst { sameAccount(it, s) }
        saveAll(list, idx)
    }

    /** Add without switching the active account. */
    suspend fun addAccount(s: LoginSession) {
        val list = getAccounts().toMutableList()
        if (list.none { sameAccount(it, s) }) list.add(s)
        saveAll(list, activeIndex())
    }

    suspend fun setActive(index: Int) {
        val list = getAccounts()
        if (index in list.indices) saveAll(list, index)
    }

    /** Remove the active account. Returns the remaining accounts. */
    suspend fun removeActive(): List<LoginSession> {
        val list = getAccounts().toMutableList()
        val idx = activeIndex()
        if (idx in list.indices) list.removeAt(idx)
        saveAll(list, 0)
        return list
    }

    /** Remove any account by index. Returns the remaining accounts. */
    suspend fun removeAt(index: Int): List<LoginSession> {
        val list = getAccounts().toMutableList()
        if (index in list.indices) list.removeAt(index)
        val active = getActive()
        val newActive = if (active != null) {
            list.indexOfFirst { sameAccount(it, active) }.coerceAtLeast(0)
        } else 0
        saveAll(list, newActive)
        return list
    }

    // ---- Old single-account API (kept for compatibility) ----

    suspend fun saveXtream(server: String, username: String, password: String) {
        saveAccount(
            LoginSession(
                type = LoginType.XTREAM,
                server = server.trim().trimEnd('/'),
                username = username.trim(),
                password = password.trim(),
            ),
        )
    }

    suspend fun saveM3u(url: String) {
        saveAccount(LoginSession(type = LoginType.M3U, m3uUrl = url.trim()))
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
