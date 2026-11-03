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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import com.streamlite.tv.data.BufferPrefStore
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * App settings (Ahmed Bhai's ask — XCIPTV-style, but only with items that
 * actually work; no dead buttons).
 *
 * - Account: multi-account manager
 * - Player Buffer: Fast / Balanced / Smooth (see PlayerFactory)
 * - Update Contents: re-sync everything from the provider
 * - Clear Cache: wipes Coil's image caches (frees TV-box storage)
 * - Sign Out: removes the active account
 * - App info + developer credit
 */
@Composable
fun SettingsScreen(
    onAccount: () -> Unit,
    onUpdateContents: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bufferLevel by remember { mutableStateOf(BufferPrefStore.level) }
    var cacheMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(32.dp),
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "account") {
                SettingRow("Account", "Add & switch accounts", onClick = onAccount)
            }

            item(key = "buffer") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F1E2E),
                        contentColor = Color.White,
                    ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Player Buffer", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Fast = quickest start on the TV box · Smooth = biggest buffer for slow lines",
                            fontSize = 13.sp,
                            color = SlTextDim,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BufferChip("Fast", BufferPrefStore.FAST, bufferLevel) {
                                bufferLevel = it
                                scope.launch { BufferPrefStore.save(context, it) }
                            }
                            BufferChip("Balanced", BufferPrefStore.BALANCED, bufferLevel) {
                                bufferLevel = it
                                scope.launch { BufferPrefStore.save(context, it) }
                            }
                            BufferChip("Smooth", BufferPrefStore.SMOOTH, bufferLevel) {
                                bufferLevel = it
                                scope.launch { BufferPrefStore.save(context, it) }
                            }
                        }
                    }
                }
            }

            item(key = "update") {
                SettingRow("Update Contents", "Re-sync channels, movies & series", onClick = onUpdateContents)
            }

            item(key = "cache") {
                SettingRow(
                    "Clear Cache",
                    if (cacheMsg.isEmpty()) "Free up TV-box storage" else cacheMsg,
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                context.imageLoader.diskCache?.clear()
                                context.imageLoader.memoryCache?.clear()
                                cacheMsg = "Cache cleared ✓"
                            } catch (_: Exception) {
                                cacheMsg = "Could not clear cache"
                            }
                        }
                    },
                )
            }

            item(key = "signout") {
                SettingRow("Sign Out", "Remove the active account", onClick = onSignOut)
            }

            item(key = "pad") { Spacer(Modifier.height(8.dp)) }
        }

        // App info + developer credit
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("StreamLite v1.0", fontSize = 13.sp, color = SlTextDim)
            Spacer(Modifier.height(8.dp))
            Text(
                "Developed by:\nAhmed Bukhari\n0332 031 3333",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F1E2E),
            contentColor = Color.White,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = SlTextDim, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun BufferChip(label: String, value: String, selected: String, onSelect: (String) -> Unit) {
    val isSel = value == selected
    Card(
        onClick = { onSelect(value) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSel) SlAccent else Color(0xFF1E3A5F),
            contentColor = if (isSel) Color.Black else Color.White,
        ),
    ) {
        Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
