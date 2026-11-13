package com.streamlite.tv.ui.screens

import android.app.Activity
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.streamlite.tv.R
import com.streamlite.tv.data.DataRepository
import com.streamlite.tv.data.ThumbnailPreloader
import kotlinx.coroutines.delay
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.ui.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.withContext

// 2026-11-13 (v2.15.0, Ahmed Bhai ka hukum): homepage ka design un ke
// bheje hue REFERENCE theme jesa kar diya gaya hai — gehra kala/maroon
// rang, upar-bayein logo, upar-dayein 4 gol buttons, 3 bare cards
// (har card ka apna rang-dar nishan: Live surkh, Movies neela,
// Series jamni), dayein QUICK ACCESS panel, aur neeche DASHBOARD
// patti (username + Status + Expiry Date). Ye rang SIRF Home ke
// liye hain — baqi screens ka theme waisa hi hai.
// NOTE: reference me "Install EPG / Reload EPG" ke buttons thay —
// wo NAHI lagaye (Ahmed Bhai ka standing hukum: EPG kabhi nahi).
// Un ki jagah Quick Access me Update media aur Account hain.
// Logo: app ka APNA naam (A.R. Xtream IPTV) reference wale style
// aur jagah par hai — kisi doosri app ka naam as-is nahi lagaya.
// Cards pop up on focus (scale + shadow, NO border); logos animate
// ONLY on remote selection — kabhi always-on nahi (un ka rule).
// 2026-11-13 (v2.16.0, Ahmed Bhai ke screenshot notes par): card
// logos REFERENCE walay kar diye (TV set + LIVE pill wala title,
// popcorn balti, kala clapperboard); neeche wale naam/count
// CHHOTE; Dashboard patti PATLI; chhoti unchai wali screen
// (landscape mobile) par cards aur Quick Access panel khud
// sikor jate hain — koi hissa kat kar gayab nahi hota.
// 2026-11-13 (v2.20.0): homepage ka background Ahmed Bhai ke
// bheje hue rang par — Rosewood #6A040F. Glow usi rang ka
// thoda roshan shade hai taake radial gehrai barqarar rahe,
// lekin page ka asal rang 6A040F hi nazar aye.
private val HomeBackground = Color(0xFF6A040F)
private val HomeGlow = Color(0xFF7D0A1B)
private val HomeCard = Color(0xFF17181F)
private val HomePanel = Color(0xFF101116)
private val HomeSurface = Color(0xFF1B1C24)
private val HomeChip = Color(0xFF26272F)
private val HomeTextDim = Color(0xFF9AA0A6)
private val LiveRed = Color(0xFFE53935)
private val MoviesBlue = Color(0xFF2196F3)
private val SeriesPurple = Color(0xFFA855F7)
private val LogoOrange = Color(0xFFFF7A1A)
private val StatusGreen = Color(0xFF4CAF50)

@Composable
fun HomeScreen(
    session: LoginSession,
    onNavigate: (Screen) -> Unit,
    // Kept for the call site: Log out was removed by his order (2026-11-05),
    // Update media (re-sync) took its place in the side column.
    onLogout: () -> Unit,
) {
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

    val liveCount = DataRepository.liveStreams?.size ?: 0
    val vodCount = DataRepository.vodTotalCount
    val seriesCount = DataRepository.seriesTotalCount

    // 2026-11-13 (v2.19.0, Point 5): QUICK ACCESS ke "Update
    // Thumbnails" button ki karwai — DataRepository khud catalog
    // load karke thumbnails chalata hai (Sync screen nahi).
    val appContext = LocalContext.current.applicationContext
    val updateThumbnails = { DataRepository.startThumbnailUpdate(session, appContext) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(HomeGlow, HomeBackground),
                    center = Offset(300f, 0f),
                    radius = 1500f,
                )
            ),
    ) {
        val isMobile = maxWidth < 600.dp
        if (isMobile) {
            // ---- MOBILE (phone): wahi naya design, compact shakl me ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                HomeHeader(compact = true, onNavigate = onNavigate)

                Spacer(Modifier.height(16.dp))

                // 2x2 grid of small cards.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeBigCard(
                        logoType = "live",
                        title = "LIVE TV",
                        bottomLabel = "Live TV",
                        count = "$liveCount Channels",
                        accent = LiveRed,
                        onClick = { onNavigate(Screen.LiveTv) },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    if (session.type == LoginType.XTREAM) {
                        HomeBigCard(
                            logoType = "movie",
                            title = "Movies",
                            count = "$vodCount Movies",
                            accent = MoviesBlue,
                            onClick = { onNavigate(Screen.Vod) },
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )
                    }
                }
                if (session.type == LoginType.XTREAM) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HomeBigCard(
                            logoType = "series",
                            title = "Series",
                            count = "$seriesCount Series",
                            accent = SeriesPurple,
                            onClick = { onNavigate(Screen.Series) },
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(16.dp))
                HomeQuickAccessPanel(onNavigate = onNavigate, onUpdateThumbnails = updateThumbnails)

                Spacer(Modifier.height(16.dp))
                HomeDashboardBar(username = session.username, expDate = expDate)

                Spacer(Modifier.height(12.dp))
                Text(
                    "Developed By: Ahmed & Wasif Bukhari",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // ---- TV (wide): reference wala layout ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 20.dp),
            ) {
                HomeHeader(compact = false, onNavigate = onNavigate)

                Spacer(Modifier.height(16.dp))

                // ---- MAIN ROW: big cards (left) + QUICK ACCESS panel ----
                // v2.16.0: chhoti unchai wali screen (landscape mobile)
                // par panel ki rows SIKOR jati hain, taake Catch Up /
                // Account wali rows kat kar gayab na hon.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    val tight = maxHeight < 300.dp
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                    Row(
                        modifier = Modifier.weight(0.72f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        HomeBigCard(
                            logoType = "live",
                            title = "LIVE TV",
                            bottomLabel = "Live TV",
                            count = "$liveCount Channels",
                            accent = LiveRed,
                            onClick = { onNavigate(Screen.LiveTv) },
                            modifier = Modifier.weight(1f),
                        )
                        if (session.type == LoginType.XTREAM) {
                            HomeBigCard(
                                logoType = "movie",
                                title = "Movies",
                                count = "$vodCount Movies",
                                accent = MoviesBlue,
                                onClick = { onNavigate(Screen.Vod) },
                                modifier = Modifier.weight(1f),
                            )
                            HomeBigCard(
                                logoType = "series",
                                title = "Series",
                                count = "$seriesCount Series",
                                accent = SeriesPurple,
                                onClick = { onNavigate(Screen.Series) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                        HomeQuickAccessPanel(
                            onNavigate = onNavigate,
                            onUpdateThumbnails = updateThumbnails,
                            modifier = Modifier.weight(0.28f).fillMaxHeight(),
                            tight = tight,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                HomeDashboardBar(username = session.username, expDate = expDate)

                Spacer(Modifier.height(10.dp))
                Text(
                    "Developed By: Ahmed & Wasif Bukhari",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Upar wali patti: bayein app ka logo + naam (reference wale style
 * me — naranji nishan ke sath), dayein 4 gol buttons (Account /
 * Settings / Update media / Exit). Exit sirf app band karta hai —
 * ye Log out NAHI hai (wo Ahmed Bhai ke hukum par pehle se hata
 * hua hai).
 */
@Composable
private fun HomeHeader(compact: Boolean, onNavigate: (Screen) -> Unit) {
    val activity = LocalContext.current as? Activity
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "A.R. Xtream IPTV logo",
            modifier = Modifier.size(if (compact) 36.dp else 42.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = LogoOrange, fontWeight = FontWeight.Bold)) {
                    append("A.R. ")
                }
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                    append("Xtream IPTV")
                }
            },
            fontSize = if (compact) 17.sp else 23.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            HomeIconButton(Icons.Filled.Person, "Account", compact) { onNavigate(Screen.Account) }
            HomeIconButton(Icons.Filled.Settings, "Settings", compact) { onNavigate(Screen.Settings) }
            HomeIconButton(Icons.Filled.Refresh, "Update media", compact) { onNavigate(Screen.Sync()) }
            HomeIconButton(Icons.Filled.ExitToApp, "Exit", compact) { activity?.finish() }
        }
    }
}

/** Gol (circle) button — focus par halka sa pop, koi border nahi. */
@Composable
private fun HomeIconButton(
    icon: ImageVector,
    label: String,
    compact: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.15f else 1f, label = "homeIconBtnScale")
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(if (compact) 36.dp else 46.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = HomeSurface,
            contentColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(if (compact) 18.dp else 22.dp),
            )
        }
    }
}

/**
 * Dayein wala QUICK ACCESS panel (reference style). EPG wale buttons
 * NAHI hain (standing hukum) — un ki jagah Update media aur Account.
 * Catch Up seedha Live TV kholta hai (app ka pehle wala rawaiya).
 */
@Composable
private fun HomeQuickAccessPanel(
    onNavigate: (Screen) -> Unit,
    onUpdateThumbnails: () -> Unit,
    modifier: Modifier = Modifier,
    tight: Boolean = false,
) {
    // 2026-11-13 (v2.18.0, Point 5): "Update Thumbnails" ki halat —
    // preload chal raha ho to progress (done / total), khatam ho
    // to kitni images load huin aur kitni FAIL (mare hue links).
    var thumbStatus by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            thumbStatus = when {
                ThumbnailPreloader.running ->
                    "Thumbnails: ${ThumbnailPreloader.done} / ${ThumbnailPreloader.total}"
                ThumbnailPreloader.total > 0 ->
                    "Thumbnails: ${ThumbnailPreloader.total - ThumbnailPreloader.failed} load, " +
                        "${ThumbnailPreloader.failed} fail"
                else -> ""
            }
            delay(700)
        }
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = HomePanel,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(if (tight) 10.dp else 16.dp)) {
            Text(
                "QUICK ACCESS",
                fontSize = if (tight) 11.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                color = HomeTextDim,
            )
            Spacer(Modifier.height(if (tight) 8.dp else 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(if (tight) 6.dp else 10.dp)) {
                HomeSideRow(
                    icon = Icons.Filled.GridView,
                    label = "Play 4 Screen",
                    onClick = { onNavigate(Screen.MultiView) },
                    tight = tight,
                )
                // 2026-11-13 (v2.20.0): panel wali "Update media"
                // row HATA di gayi — Ahmed Bhai ka hukum: upar
                // Settings ke sath Update media ka button pehle
                // se mojood hai, neeche wale ki zarurat nahi.
                // 2026-11-13 (v2.18.0, Point 5): Ahmed Bhai ka
                // hukum — sirf title images/logos taaza karne ka
                // ALAG button. v2.19.0: button ab khud-kafi hai —
                // DataRepository pehle catalog load karke phir
                // thumbnails chalata hai; Sync screen par bhejna
                // khatam (wo rawaiya "button kaam nahi karta" ki
                // wajah tha).
                HomeSideRow(
                    icon = Icons.Filled.Image,
                    label = "Update Thumbnails",
                    onClick = onUpdateThumbnails,
                    tight = tight,
                )
                HomeSideRow(
                    icon = Icons.Filled.History,
                    label = "Catch Up",
                    onClick = { onNavigate(Screen.LiveTv) },
                    tight = tight,
                )
                HomeSideRow(
                    icon = Icons.Filled.Person,
                    label = "Account",
                    onClick = { onNavigate(Screen.Account) },
                    tight = tight,
                )
            }
            // Thumbnails ki halat (chal rahi ho to progress,
            // khatam ho to load/fail ka hisaab) — Point 5.
            if (thumbStatus.isNotEmpty()) {
                Spacer(Modifier.height(if (tight) 6.dp else 10.dp))
                Text(
                    thumbStatus,
                    fontSize = if (tight) 11.sp else 12.sp,
                    color = HomeTextDim,
                )
            }
        }
    }
}

/**
 * Neeche wali DASHBOARD patti (reference style): bayein username,
 * dayein STATUS Active + EXPIRY DATE (sabz rang me). Reference me
 * jo device ID ("1ca99cc4") thi, wo is app me nahi hoti — us ki
 * jagah account ka username hai.
 */
@Composable
private fun HomeDashboardBar(username: String, expDate: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = HomePanel,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "DASHBOARD",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeTextDim,
                )
                Text(
                    username,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            Text("STATUS", fontSize = 10.sp, color = HomeTextDim)
            Text(
                "  Active",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = StatusGreen,
            )
            if (expDate > 0) {
                Spacer(Modifier.width(22.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(StatusGreen, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text("EXPIRY DATE:  ", fontSize = 10.sp, color = HomeTextDim)
                Text(
                    SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date(expDate * 1000)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = StatusGreen,
                )
            }
        }
    }
}

/**
 * Bara card (LIVE TV / Movies / Series) — reference style: logo
 * upar-darmiyan, title us ke neeche, aur sab se neeche-bayein
 * rang-dar patti ke sath naam + count. Card focus par POP hota hai
 * (scale + shadow, NO border). LOGO sirf remote selection (focus)
 * par animate hota hai — NEVER always-on (un ka rule):
 * LIVE = pulse, Movies reel = spin, Series = wobble.
 * compact = true on phones: chhota card (150dp).
 */
@Composable
private fun HomeBigCard(
    logoType: String = "live",
    title: String,
    bottomLabel: String = title,
    count: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "homeBigCardScale")
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
    // RULE (his, 2026-11-05): logos animate ONLY on remote selection
    // (focus) — NEVER always-on.
    // (Icon/title ke sizes neeche BoxWithConstraints se ate hain —
    // chhoti unchai par wo khud sikor jate hain.)
    val logoModifier = when (logoType) {
        "movie" -> Modifier.rotate(if (isFocused) spin else 0f)
        "series" -> Modifier.rotate(if (isFocused) wobble else 0f)
            .scale(if (isFocused) pulse else 1f)
        else -> Modifier.scale(if (isFocused) pulse else 1f) // live: pulse
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .then(if (compact) Modifier.height(150.dp) else Modifier.fillMaxHeight())
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = HomeCard,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 12.dp else 4.dp,
        ),
    ) {
        // v2.16.0: card ki asal unchai dekh kar andar ke sizes —
        // landscape mobile jesi chhoti screen par logo/title sikor
        // jate hain, taake neeche wali count ki line card se bahar
        // kat kar gayab na ho (Ahmed Bhai ki screenshot wala masla).
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val small = !compact && maxHeight < 260.dp
            val iconSize = when {
                compact -> 48.dp
                small -> 58.dp
                else -> 92.dp
            }
            val titleSize = when {
                compact -> 19.sp
                small -> 20.sp
                else -> 26.sp
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 16.dp),
            ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HomeLogo(logoType = logoType, size = iconSize, modifier = logoModifier)
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                if (logoType == "live") {
                    // Reference style title: surkh LIVE pill + TV.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(LiveRed, RoundedCornerShape(50))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "LIVE",
                                fontSize = if (compact) 11.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "TV",
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                } else {
                    Text(
                        title,
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // Neeche-bayein: rang-dar patti + naam + count + refresh
            // ka chhota nishan (reference style).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = if (compact) 24.dp else 32.dp)
                        .background(accent, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        bottomLabel,
                        fontSize = if (compact) 11.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            count,
                            fontSize = if (compact) 10.sp else 12.sp,
                            color = HomeTextDim,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(if (compact) 11.dp else 12.dp),
                        )
                    }
                }
            }
            }
        }
    }
}

/**
 * Card logos — v2.16.0: REFERENCE image walay (Ahmed Bhai ka
 * hukum): LIVE = TV set (antenna wala), title ke sath surkh LIVE
 * pill (HomeBigCard me lagti hai); Movies = popcorn ki balti
 * (safed + surkh dhariyaan, upar peele dane); Series = KALA
 * clapperboard safed dhariyon ke sath.
 */
@Composable
private fun HomeLogo(logoType: String, size: Dp, modifier: Modifier = Modifier) {
    when (logoType) {
        "live" -> TvLogo(size, modifier)
        "movie" -> PopcornLogo(size, modifier)
        "series" -> ClapperLogo(size, modifier)
        else -> Icon(
            Icons.Filled.GridView,
            contentDescription = "4 Screen",
            tint = SeriesPurple,
            modifier = modifier.size(size),
        )
    }
}

@Composable
private fun TvLogo(logoSize: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(logoSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val body = Color(0xFF23252D)
            val edge = Color(0xFF3A3F4A)
            // Antenna: darmiyan ki bunyad se do terchi taangein,
            // siro par gol nishan (reference wala TV set).
            val base = Offset(cx, h * 0.30f)
            val tipL = Offset(w * 0.20f, h * 0.03f)
            val tipR = Offset(w * 0.80f, h * 0.03f)
            val antW = (w * 0.028f).coerceAtLeast(2f)
            drawLine(edge, base, tipL, strokeWidth = antW)
            drawLine(edge, base, tipR, strokeWidth = antW)
            drawCircle(edge, radius = w * 0.032f, center = tipL)
            drawCircle(edge, radius = w * 0.032f, center = tipR)
            drawCircle(edge, radius = w * 0.045f, center = base)
            // TV body (gehra slate) + andar ki kaali screen.
            drawRoundRect(
                body,
                topLeft = Offset(w * 0.09f, h * 0.28f),
                size = Size(w * 0.82f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
            )
            drawRoundRect(
                Color(0xFF0C0D11),
                topLeft = Offset(w * 0.155f, h * 0.345f),
                size = Size(w * 0.69f, h * 0.43f),
                cornerRadius = CornerRadius(w * 0.045f, w * 0.045f),
            )
            // Neeche chhota sa stand.
            drawRoundRect(
                edge,
                topLeft = Offset(w * 0.36f, h * 0.865f),
                size = Size(w * 0.28f, h * 0.045f),
                cornerRadius = CornerRadius(w * 0.02f, w * 0.02f),
            )
        }
        // Screen ke darmiyan safed "TV".
        Text(
            "TV",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (logoSize.value * 0.26f).sp,
            modifier = Modifier.padding(top = (logoSize.value * 0.11f).dp),
        )
    }
}

@Composable
private fun PopcornLogo(logoSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(logoSize)) {
        val w = size.width
        val h = size.height
        // Balti (bucket): upar chauri, neeche tang — trapezoid.
        val bucket = Path().apply {
            moveTo(w * 0.235f, h * 0.30f)
            lineTo(w * 0.765f, h * 0.30f)
            lineTo(w * 0.685f, h * 0.90f)
            lineTo(w * 0.315f, h * 0.90f)
            close()
        }
        drawPath(bucket, Color(0xFFF5F5F5))
        // Surkh khari dhariyaan — SIRF balti ki shakl ke andar.
        // Canvas par clip karke karna parta hai: DrawScope.clipPath
        // is Compose version me mojood nahi — v2.16.0 ki pehli
        // build THIK isi line par fail hui thi (compile error:
        // unresolved reference 'clipPath'). Ye seedha canvas wala
        // tareeqa har version me chalta hai.
        drawContext.canvas.save()
        drawContext.canvas.clipPath(bucket)
        val sw = w * 0.105f
        for (i in 0 until 3) {
            val x = w * (0.335f + i * 0.165f)
            drawRect(
                LiveRed,
                topLeft = Offset(x, h * 0.30f),
                size = Size(sw, h * 0.60f),
            )
        }
        drawContext.canvas.restore()
        // Balti ke kinare (rim) ki halki lakeer.
        drawLine(
            Color(0xFFD8D8D8),
            Offset(w * 0.225f, h * 0.30f),
            Offset(w * 0.775f, h * 0.30f),
            strokeWidth = (w * 0.02f).coerceAtLeast(1.5f),
        )
        // Upar popcorn ke dane: halke peele gol + naram sa saya.
        val cream = Color(0xFFFFE9B0)
        val shade = Color(0xFFF2C14E)
        val kern = listOf(
            Triple(0.32f, 0.235f, 0.105f),
            Triple(0.435f, 0.185f, 0.125f),
            Triple(0.545f, 0.205f, 0.115f),
            Triple(0.65f, 0.19f, 0.12f),
            Triple(0.735f, 0.25f, 0.095f),
            Triple(0.50f, 0.27f, 0.10f),
        )
        for ((fx, fy, fr) in kern) {
            drawCircle(cream, radius = w * fr, center = Offset(w * fx, h * fy))
            drawCircle(
                shade,
                radius = w * fr * 0.38f,
                center = Offset(w * fx + w * fr * 0.25f, h * fy + w * fr * 0.30f),
            )
        }
    }
}

@Composable
private fun ClapperLogo(logoSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(logoSize)) {
        val w = size.width
        val h = size.height
        val bx = w * 0.08f
        val by = h * 0.30f
        val bw = w * 0.84f
        val bh = h * 0.60f
        // v2.16.0: KALA body (reference wala clapper) + halki kinari.
        drawRoundRect(
            Color(0xFF17181C),
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
        )
        drawRoundRect(
            Color(0xFF3A3F4A),
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
            style = Stroke(width = (w * 0.014f).coerceAtLeast(1.5f)),
        )
        // Top slate bar (kala) with white diagonal stripes.
        val barH = h * 0.20f
        val barTop = by - barH * 0.55f
        drawRoundRect(
            Color(0xFF0B0B0F),
            topLeft = Offset(bx, barTop),
            size = Size(bw, barH),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        )
        val sw = bw / 4.5f
        for (i in 0 until 4) {
            val x0 = bx + sw * (i + 0.9f)
            drawLine(
                Color.White,
                Offset(x0, barTop),
                Offset(x0 - sw * 0.55f, barTop + barH),
                strokeWidth = w * 0.045f,
            )
        }
        // Small white label rect on the body.
        drawRoundRect(
            Color.White,
            topLeft = Offset(bx + bw * 0.36f, by + bh * 0.58f),
            size = Size(bw * 0.28f, bh * 0.16f),
            cornerRadius = CornerRadius(2f, 2f),
        )
    }
}

/**
 * Quick Access ki row: gehray gol chip me safed icon + naam.
 * Focus par halka pop — koi border nahi.
 */
@Composable
private fun HomeSideRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tight: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1f, label = "homeSideRowScale")
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = HomeSurface,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 8.dp else 0.dp,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (tight) 6.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (tight) 28.dp else 34.dp)
                    .background(HomeChip, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(if (tight) 15.dp else 19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = if (tight) 14.sp else 16.sp, color = Color.White)
        }
    }
}
