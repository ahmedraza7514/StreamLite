package com.streamlite.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.M3uChannel
import com.streamlite.tv.data.SessionManager
import com.streamlite.tv.ui.Screen
import com.streamlite.tv.ui.screens.HomeScreen
import com.streamlite.tv.ui.screens.LiveTvScreen
import com.streamlite.tv.ui.screens.LoginScreen
import com.streamlite.tv.ui.screens.PlayerScreen
import com.streamlite.tv.ui.screens.SeriesDetailScreen
import com.streamlite.tv.ui.screens.SeriesScreen
import com.streamlite.tv.ui.screens.VodScreen
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.StreamLiteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StreamLiteTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val navStack = remember { mutableStateListOf<Screen>() }
    var session by remember { mutableStateOf<LoginSession?>(null) }
    var booted by remember { mutableStateOf(false) }
    val m3uCache: MutableState<List<M3uChannel>?> = remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        session = sessionManager.getOnce()
        navStack.add(if (session != null) Screen.Home else Screen.Login)
        booted = true
    }

    BackHandler(enabled = booted && navStack.size > 1) {
        navStack.removeLast()
    }

    if (!booted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SlBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text("StreamLite", style = MaterialTheme.typography.displaySmall, color = SlAccent)
        }
        return
    }

    fun goHome(s: LoginSession) {
        session = s
        navStack.clear()
        navStack.add(Screen.Home)
    }

    fun logout() {
        scope.launch {
            sessionManager.clear()
            m3uCache.value = null
            session = null
            navStack.clear()
            navStack.add(Screen.Login)
        }
    }

    fun play(url: String, title: String) {
        navStack.add(Screen.Player(url, title))
    }

    when (val s = navStack.last()) {
        is Screen.Login -> LoginScreen(sessionManager, ::goHome)
        is Screen.Home -> HomeScreen(session!!, navStack::add, ::logout)
        is Screen.LiveTv -> LiveTvScreen(session!!, m3uCache, ::play)
        is Screen.Vod -> VodScreen(session!!, ::play)
        is Screen.Series -> SeriesScreen(session!!) { id, title ->
            navStack.add(Screen.SeriesDetail(id, title))
        }
        is Screen.SeriesDetail -> SeriesDetailScreen(session!!, s.seriesId, s.title, ::play)
        is Screen.Player -> PlayerScreen(s.url, s.title)
    }
}
