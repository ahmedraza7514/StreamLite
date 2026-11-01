package com.streamlite.tv.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streamlite.tv.data.DataRepository
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.launch

/**
 * XCIPTV-style "Update Media Contents" screen. Shown once after login,
 * bulk-loads LIVE TV / VOD / SERIES / GUIDE with progress, then continues.
 */
@Composable
fun SyncScreen(
    session: LoginSession,
    onSyncComplete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf("Waiting...") }
    var completedSteps by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var epgDone by remember { mutableStateOf(0) }
    var epgTotal by remember { mutableStateOf(0) }

    val steps = listOf("LIVE TV", "VOD", "SERIES", "GUIDE")

    fun startSync() {
        error = null
        completedSteps = emptySet()
        currentStep = "Waiting..."
        epgDone = 0
        epgTotal = 0
        scope.launch {
            try {
                if (session.type == LoginType.XTREAM) {
                    val client = XtreamClient(session.server, session.username, session.password)
                    DataRepository.syncAll(
                        client,
                        onProgress = { step ->
                            if (step == "DONE") {
                                completedSteps = steps.toSet()
                            } else {
                                currentStep = step
                                // Mark previous steps as done
                                val idx = steps.indexOf(step)
                                if (idx > 0) {
                                    completedSteps = steps.take(idx).toSet()
                                }
                            }
                        },
                        onEpgProgress = { done, total ->
                            epgDone = done
                            epgTotal = total
                        },
                    )
                } else {
                    // M3U: nothing to bulk sync
                    completedSteps = steps.toSet()
                }
                onSyncComplete()
            } catch (e: Exception) {
                error = e.message ?: "Sync failed"
            }
        }
    }

    LaunchedEffect(Unit) { startSync() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Update Media Contents",
            style = MaterialTheme.typography.headlineMedium,
            color = SlText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        // 4 boxes: LIVE TV | VOD | SERIES | GUIDE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            steps.forEach { step ->
                val isDone = step in completedSteps
                val isActive = currentStep == step && !isDone
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isDone -> Color(0xFF1B4D2E) // green for done
                            isActive -> Color(0xFF1A3A5C) // blue for active
                            else -> Color(0xFF0F1E2E) // dark for waiting
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            step,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SlText,
                        )
                        Spacer(Modifier.height(8.dp))
                        when {
                            isDone -> Text("✓ Done", fontSize = 14.sp, color = Color(0xFF4CAF50))
                            isActive -> {
                                if (step == "GUIDE" && epgTotal > 0) {
                                    // Real EPG progress: "1234 / 5000"
                                    Text(
                                        "$epgDone / $epgTotal",
                                        fontSize = 13.sp,
                                        color = SlAccent,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        color = SlAccent,
                                        modifier = Modifier.height(20.dp).width(20.dp),
                                    )
                                }
                            }
                            else -> Text("Waiting...", fontSize = 14.sp, color = SlTextDim)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        if (error != null) {
            Text(error!!, color = Color(0xFFFF6B6B), fontSize = 15.sp)
            Spacer(Modifier.height(16.dp))
            Card(
                onClick = { startSync() },
                colors = CardDefaults.cardColors(containerColor = SlAccent, contentColor = Color.Black),
            ) {
                Text("Retry", modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
            }
        } else {
            Text("Please wait.......", fontSize = 16.sp, color = SlTextDim)
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Developed By: Ahmed Raza Bukhari +92 332 0313333",
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
