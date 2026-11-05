package com.streamlite.tv.ui.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streamlite.tv.R
import com.streamlite.tv.data.DataRepository
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Home-only AZUL theme (Batch 8 fix): ONLY the home screen uses the AZUL
// purple palette (100% the reference colors) — the rest of the app keeps
// the original blue theme (Theme.kt reverted).
private val HomeBackground = Color(0xFF14141F)
private val HomeSurface = Color(0xFF232338)
private val HomeCard = Color(0xFF2E2E48)
private val HomeAccent = Color(0xFFA855F7)
private val HomeTextDim = Color(0xFFB0AEC9)

// 2026-11-05 (Batch 7): AZUL-style home — app logo + name top-left, live
// date/time + account expiration top-right, three big horizontal cards
// (LIVE TV / Movies / Series / 4 Screen) with counts, right column (
// Multi-Screen / Settings / Account / Log out),
// "Developed By: Ahmed & Wasif Bukhari" at the bottom. Cards POP UP
// (scale + white border + shadow) on D-pad focus / mouse hover.

@Composable
fun HomeScreen(
    session: LoginSession,
    onNavigate: (Screen) -> Unit,
    onLogout: () -> Unit,
) {
    // Live clock (ticks every 20s — minute precision is enough).
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }
    val dateText = remember(now) {
        SimpleDateFormat("MMM d, yyyy - hh:mm a", Locale.ENGLISH).format(Date(now))
    }

    // Account expiration from Xtream user_info.exp_date. Saved sessions from
    // before this build have 0 — fetch it once in the background (Xtream only).
    var expDate by remember { mutableStateOf(session.expDate) }
    LaunchedEffect(Unit) {
        if (expDate == 0L && session.type == LoginType.XTREAM) {
            try {
                val exp = withContext(Dispatchers.IO) {
                    XtreamClient(session.server, session.username, session.password).expDate()
                }
                if (exp > 0) expDate = exp
            } catch (_: Exception) {
                // No network / panel error — expiration line just stays hidden.
            }
        }
    }
    val expText = if (expDate > 0) {
        "Expiration: " + SimpleDateFormat("d MMM, yyyy", Locale.ENGLISH)
            .format(Date(expDate * 1000))
    } else {
        null
    }

    val liveCount = DataRepository.liveStreams?.size ?: 0
    val vodCount = DataRepository.vodTotalCount
    val seriesCount = DataRepository.seriesTotalCount

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        // ---- HEADER: logo + app name (left), date/time + expiration (right) ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "A.R. Xtream IPTV logo",
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "A.R. Xtream IPTV",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = HomeAccent,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(dateText, fontSize = 16.sp, color = Color.White)
                if (expText != null) {
                    Text(
                        expText,
                        fontSize = 14.sp,
                        color = HomeTextDim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ---- MAIN ROW: 3 big cards (left) + right column ----
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Big horizontal cards
            Row(
                modifier = Modifier.weight(0.72f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                HomeBigCard(
                    icon = Icons.Filled.LiveTv,
                    logoType = "live",
                    title = "LIVE TV",
                    count = "• $liveCount Channels",
                    onClick = { onNavigate(Screen.LiveTv) },
                    modifier = Modifier.weight(1f),
                )
                if (session.type == LoginType.XTREAM) {
                    HomeBigCard(
                        icon = Icons.Filled.Movie,
                        logoType = "movie",
                        title = "Movies",
                        count = "• $vodCount Movies",
                        onClick = { onNavigate(Screen.Vod) },
                        modifier = Modifier.weight(1f),
                    )
                    HomeBigCard(
                        icon = Icons.Filled.VideoLibrary,
                        logoType = "series",
                        title = "Series",
                        count = "• $seriesCount Series",
                        onClick = { onNavigate(Screen.Series) },
                        modifier = Modifier.weight(1f),
                    )
                    // 2026-11-05 (Batch 8 fix): 4th big card — 4 Screen, same
                    // size and same design as Live TV / Movies / Series.
                    HomeBigCard(
                        icon = Icons.Filled.GridView,
                        logoType = "grid",
                        title = "4 Screen",
                        count = "• Multi-Screen",
                        onClick = { onNavigate(Screen.MultiView) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Right column — reference order first, then the rest of the app.
            Column(
                modifier = Modifier.weight(0.28f),
                // 4 rows in ~360dp of TV height — compact rows + 8dp gaps.
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeSideRow(
                    icon = Icons.Filled.GridView,
                    label = "Multi-Screen",
                    onClick = { onNavigate(Screen.MultiView) },
                )
                HomeSideRow(
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    onClick = { onNavigate(Screen.Settings) },
                )
                HomeSideRow(
                    icon = Icons.Filled.Person,
                    label = "Account",
                    onClick = { onNavigate(Screen.Account) },
                )
                HomeSideRow(
                    icon = Icons.Filled.Logout,
                    label = "Log out",
                    onClick = onLogout,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Developed By: Ahmed & Wasif Bukhari",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Big horizontal card (LIVE TV / Movies / Series): icon on top, bold title,
 * count below. POPS UP on D-pad focus / mouse hover — scale + white border
 * + shadow, like the reference interface.
 */
@Composable
private fun HomeBigCard(
    icon: ImageVector,
    logoType: String = "live",
    title: String,
    count: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "homeBigCardScale")
    // 2026-11-05 (Batch 8 fix): each logo ANIMATES continuously while the
    // remote selection is on its card — NO sound, visual only.
    // LIVE = pulse, Movies reel = spins, Series = wobble (clapper).
    val infinite = rememberInfiniteTransition(label = "homeLogoAnim")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "logoPulse",
    )
    val spin by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "logoSpin",
    )
    val wobble by infinite.animateFloat(
        initialValue = -10f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "logoWobble",
    )
    val iconModifier = when (logoType) {
        "movie" -> Modifier.size(80.dp).rotate(if (isFocused) spin else 0f)
        "series" -> Modifier.size(80.dp)
            .rotate(if (isFocused) wobble else 0f)
            .scale(if (isFocused) pulse else 1f)
        else -> Modifier.size(80.dp).scale(if (isFocused) pulse else 1f) // live: pulse
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .height(280.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) HomeCard else HomeSurface,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 12.dp else 4.dp,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = title, tint = HomeAccent, modifier = iconModifier)
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(count, fontSize = 18.sp, color = HomeTextDim)
        }
    }
}

/** Right-column row (icon + label), same popup-on-focus behaviour. */
@Composable
private fun HomeSideRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "homeSideRowScale")
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) HomeCard else HomeSurface,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = label, tint = HomeAccent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 18.sp, color = Color.White)
        }
    }
}
