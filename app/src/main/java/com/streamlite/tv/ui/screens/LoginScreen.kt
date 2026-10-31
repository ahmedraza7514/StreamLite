package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
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
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun doLogin() {
        if (loading) return
        error = null
        loading = true
        scope.launch {
            try {
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("StreamLite", style = MaterialTheme.typography.displaySmall, color = SlAccent)
        Text(
            "Log in with your Xtream Codes IPTV account",
            fontSize = 15.sp,
            color = SlTextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        FieldLabel("Server URL")
        TvTextField(
            value = server, onValueChange = { server = it },
            placeholder = "http://myserver.to:8080",
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(16.dp))

        FieldLabel("Username")
        TvTextField(
            value = username, onValueChange = { username = it },
            placeholder = "Your username",
        )
        Spacer(Modifier.height(16.dp))

        FieldLabel("Password")
        TvTextField(
            value = password, onValueChange = { password = it },
            placeholder = "Your password", password = true,
            imeAction = ImeAction.Done, onImeAction = { doLogin() },
        )
        Spacer(Modifier.height(28.dp))

        Card(
            onClick = { doLogin() },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            colors = CardDefaults.cardColors(
                containerColor = SlAccent,
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.height(26.dp).width(26.dp),
                    )
                } else {
                    Text("Login", fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
private fun FieldLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = SlTextDim,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
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
            .background(SlSurface, RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 15.dp),
        textStyle = TextStyle(color = SlText, fontSize = 17.sp),
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
                if (value.isEmpty()) Text(placeholder, color = SlTextDim, fontSize = 16.sp)
                inner()
            }
        },
    )
}
