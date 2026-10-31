package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.M3uParser
import com.streamlite.tv.data.SessionManager
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlSurface
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    onLoggedIn: (LoginSession) -> Unit,
) {
    var tab by remember { mutableStateOf(0) } // 0 = Xtream Codes, 1 = M3U playlist
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun doLogin() {
        if (loading) return
        error = null
        loading = true
        scope.launch {
            try {
                if (tab == 0) {
                    if (server.isBlank() || username.isBlank() || password.isBlank()) {
                        throw IOException("Please fill in server, username and password.")
                    }
                    val active = withContext(Dispatchers.IO) {
                        XtreamClient(server, username, password).login()
                    }
                    if (!active) throw IOException("This account is not active.")
                    sessionManager.saveXtream(server, username, password)
                    onLoggedIn(
                        LoginSession(
                            type = LoginType.XTREAM,
                            server = server.trim().trimEnd('/'),
                            username = username.trim(),
                            password = password.trim(),
                        )
                    )
                } else {
                    if (m3uUrl.isBlank()) throw IOException("Please enter the playlist URL.")
                    withContext(Dispatchers.IO) { M3uParser.download(m3uUrl) } // validates the list
                    sessionManager.saveM3u(m3uUrl)
                    onLoggedIn(LoginSession(type = LoginType.M3U, m3uUrl = m3uUrl.trim()))
                }
            } catch (e: Exception) {
                error = e.message ?: "Login failed. Check the details and try again."
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(horizontal = 120.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("StreamLite", style = MaterialTheme.typography.displaySmall, color = SlAccent)
        Text(
            "Your lightweight TV player",
            fontSize = 16.sp,
            color = SlTextDim,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoginTab("Xtream Codes", selected = tab == 0) { tab = 0 }
            LoginTab("M3U Playlist", selected = tab == 1) { tab = 1 }
        }

        Spacer(Modifier.height(24.dp))

        if (tab == 0) {
            TvTextField(
                value = server, onValueChange = { server = it },
                placeholder = "Server URL  (http://example.tv:8080)",
                keyboardType = KeyboardType.Uri,
            )
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = username, onValueChange = { username = it },
                placeholder = "Username",
            )
            Spacer(Modifier.height(12.dp))
            TvTextField(
                value = password, onValueChange = { password = it },
                placeholder = "Password", password = true,
                imeAction = ImeAction.Done, onImeAction = { doLogin() },
            )
        } else {
            TvTextField(
                value = m3uUrl, onValueChange = { m3uUrl = it },
                placeholder = "Playlist URL  (http://example.tv/playlist.m3u)",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done, onImeAction = { doLogin() },
            )
            Text(
                "Movies and series sections are hidden in M3U mode.",
                fontSize = 13.sp, color = SlTextDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Card(
            onClick = { doLogin() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SlAccent,
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.height(24.dp).width(24.dp))
                } else {
                    Text("Log in", fontSize = 18.sp)
                }
            }
        }

        error?.let {
            Text(
                it, color = Color(0xFFFF6B6B), fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun LoginTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) SlAccent else SlSurface,
            contentColor = if (selected) Color.Black else SlText,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Text(text, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp))
    }
}

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (focused) SlAccent else Color(0xFF2A4258),
                shape = RoundedCornerShape(10.dp),
            )
            .background(SlSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        textStyle = TextStyle(color = SlText, fontSize = 18.sp),
        cursorBrush = SolidColor(SlAccent),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { onImeAction() },
        ),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = SlTextDim, fontSize = 17.sp)
                inner()
            }
        },
    )
}
