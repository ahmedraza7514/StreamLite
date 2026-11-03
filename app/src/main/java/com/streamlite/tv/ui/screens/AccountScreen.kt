package com.streamlite.tv.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.SessionManager
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.launch

/**
 * Multi-account manager (Ahmed Bhai's ask): add several Xtream accounts,
 * tap one to switch to it, remove the ones you don't need.
 * Fully remote-friendly: every row is a focusable card.
 */
@Composable
fun AccountScreen(
    sessionManager: SessionManager,
    active: LoginSession?,
    onSwitch: (LoginSession) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val accounts by sessionManager.accountsFlow().collectAsState(initial = emptyList())

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlBackground)
            .padding(32.dp),
    ) {
        Text("Accounts", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Add several accounts and switch to whichever you want to watch",
            fontSize = 14.sp,
            color = SlTextDim,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(accounts, key = { _, a -> a.label() }) { index, account ->
                val isActive = account == active
                Card(
                    onClick = { if (!isActive) onSwitch(account) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) Color(0xFF1B4D2E) else Color(0xFF0F1E2E),
                        contentColor = Color.White,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                account.label(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                            )
                            Text(
                                if (isActive) "ACTIVE — currently in use"
                                else "Tap to switch to this account",
                                fontSize = 13.sp,
                                color = if (isActive) Color(0xFF4CAF50) else SlTextDim,
                            )
                        }
                        if (!isActive && accounts.size > 1) {
                            Card(
                                onClick = {
                                    scope.launch { sessionManager.removeAt(index) }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF3A1A1A),
                                    contentColor = Color(0xFFFF8A8A),
                                ),
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Text("Remove", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "add") {
                Card(
                    onClick = onAdd,
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SlAccent,
                        contentColor = Color.Black,
                    ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("+ Add Account", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.height(16.dp)) }
        }
    }
}
