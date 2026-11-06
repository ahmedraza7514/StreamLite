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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// Home-only AZUL theme (Batch 8 fix): ONLY the home screen uses the AZUL
// purple palette — the rest of the app keeps the original blue theme
// (Theme.kt reverted).
// 2026-11-05 (Fix 3): colors MEASURED from his AZUL reference screenshot
// (region-averaged, not eyeballed): bg #1D182C, card #2E2B3C,
// side-row surface #312E3F, accent #A855F7, text-dim #B0AEC9.
// 2026-11-06 (v2.12): the reference VIDEO frame measures the background at
// #2D2748 — the old #1D182C was far too dark. Background is now #2A2547
// (card #2E2B3C was already right and stays).
private val HomeBackground = Color(0xFF2A2547)
private val HomeSurface = Color(0xFF312E3F)
private val HomeCard = Color(0xFF2E2B3C)
private val HomeAccent = Color(0xFFA855F7)
private val HomeTextDim = Color(0xFFB0AEC9)

// 2026-11-05 (Batch 7): AZUL-style home — app logo + name top-left, live
// date/time + account expiration top-right, 3 big cards (LIVE TV / Movies /
// Series) with counts, right column (4 Screen / Settings / Account /
// Update media), "Developed By: Ahmed & Wasif Bukhari" at the bottom.
// 2026-11-05 (his order): 4 Screen moved to the side column; the old
// Multi-Screen row and Log out are removed (Update media = re-sync).
// Cards POP UP (scale + white border + shadow) on D-pad focus / mouse hover.
// 2026-11-05 (Fix 3): logos animate ONLY on remote selection (focus) —
// NEVER always-on (his explicit rule; mobile may not animate at all).
// On narrow screens (<600dp, mobile) the layout switches to a compact
// 2x2 grid of small cards + side rows below, so everything fits the
// phone screen.

@Composable
fun HomeScreen(
    session: LoginSession,
    onNavigate: (Screen) -> Unit,
    // Kept for the call site: Log out was removed by his order (2026-11-05),
    // Update media (re-sync) took its place in the side column.
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(HomeBackground),
    ) {
        val isMobile = maxWidth < 600.dp
        if (isMobile) {
            // ---- MOBILE (phone): compact 2x2 grid of small cards ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                // Header, compact.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "A.R. Xtream IPTV logo",
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "A.R. Xtream IPTV",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = HomeAccent,
                    )
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(dateText, fontSize = 12.sp, color = Color.White)
                        if (expText != null) {
                            Text(
                                expText,
                                fontSize = 11.sp,
                                color = HomeTextDim,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 2x2 grid of small cards.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HomeBigCard(
                            logoType = "live",
                        title = "LIVE TV",
                        count = "$liveCount Channels",
                        onClick = { onNavigate(Screen.LiveTv) },
                        modifier = Modifier.weight(1f),
                        compact = true,
                    )
                    if (session.type == LoginType.XTREAM) {
                        HomeBigCard(
                                    logoType = "movie",
                            title = "Movies",
                            count = "$vodCount Movies",
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
                            onClick = { onNavigate(Screen.Series) },
                            modifier = Modifier.weight(1f),
                            compact = true,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Side rows, full width, one below the other.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 2026-11-05 (his order): 4 Screen lives in the side
                    // column now — main cards are only Live TV / Movies / Series.
                    HomeSideRow(
                        icon = Icons.Filled.GridView,
                        label = "4 Screen",
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
                    // 2026-11-05 (his order): Log out removed; Update media
                    // (re-sync) takes its place.
                    HomeSideRow(
                        icon = Icons.Filled.Refresh,
                        label = "Update media",
                        onClick = { onNavigate(Screen.Sync()) },
                    )
                }

                Spacer(Modifier.height(16.dp))
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
            // ---- TV (wide): original big-card layout, unchanged ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
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

                // ---- MAIN ROW: big cards (left) + right column ----
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
                                    logoType = "live",
                            title = "LIVE TV",
                            count = "$liveCount Channels",
                            onClick = { onNavigate(Screen.LiveTv) },
                            modifier = Modifier.weight(1f),
                        )
                        if (session.type == LoginType.XTREAM) {
                            HomeBigCard(
                                            logoType = "movie",
                                title = "Movies",
                                count = "$vodCount Movies",
                                onClick = { onNavigate(Screen.Vod) },
                                modifier = Modifier.weight(1f),
                            )
                            HomeBigCard(
                                            logoType = "series",
                                title = "Series",
                                count = "$seriesCount Series",
                                onClick = { onNavigate(Screen.Series) },
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
                        // 2026-11-05 (his order): 4 Screen lives in the side
                        // column now — main cards are only Live TV / Movies / Series.
                        HomeSideRow(
                            icon = Icons.Filled.GridView,
                            label = "4 Screen",
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
                        // 2026-11-05 (his order): Log out removed; Update media
                        // (re-sync) takes its place.
                        HomeSideRow(
                            icon = Icons.Filled.Refresh,
                            label = "Update media",
                            onClick = { onNavigate(Screen.Sync()) },
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
    }
}

/**
 * Big card (LIVE TV / Movies / Series / 4 Screen): icon on top, bold title,
 * count below. POPS UP on D-pad focus / mouse hover — scale + white border
 * + shadow, like the reference interface.
 * The LOGO animates ONLY while its card has remote selection (focus):
 * LIVE = pulse, Movies reel = spins, Series = wobble, 4 Screen = pulse.
 * NEVER always-on (his rule). NO sound, visual only.
 * compact = true on phones: small card (150dp, 48dp icon, 20sp title).
 */
@Composable
private fun HomeBigCard(
    logoType: String = "live",
    title: String,
    count: String,
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
    // (focus) — NEVER always-on. Mobile may show no animation at all;
    // on the TV box only the element under selection animates.
    val iconSize = if (compact) 48.dp else 80.dp
    val logoModifier = when (logoType) {
        "movie" -> Modifier.rotate(if (isFocused) spin else 0f)
        "series" -> Modifier.rotate(if (isFocused) wobble else 0f)
            .scale(if (isFocused) pulse else 1f)
        else -> Modifier.scale(if (isFocused) pulse else 1f) // live + grid: pulse
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .height(if (compact) 150.dp else 280.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scale)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = HomeCard,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 12.dp else 4.dp,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HomeLogo(logoType = logoType, size = iconSize, modifier = logoModifier)
            Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            Text(
                title,
                fontSize = if (compact) 20.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
            // v2.12: the bullet is the purple accent (like the AZUL
            // reference), the count text stays dim.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = HomeAccent, fontWeight = FontWeight.Bold)) {
                        append("• ")
                    }
                    withStyle(SpanStyle(color = HomeTextDim)) { append(count) }
                },
                fontSize = if (compact) 13.sp else 18.sp,
            )
        }
    }
}


/**
 * Reference-style logos (Fix 3, 2026-11-05): drawn to match the AZUL
 * reference — NOT generic Material icons. LIVE = pink broadcast antenna
 * with signal waves + white LIVE sign (v2.12: the old white TV set did
 * NOT match the reference and is removed); Movies = white film reel +
 * purple film strip; Series = purple clapperboard with white stripes;
 * 4 Screen = grid icon.
 */
@Composable
private fun HomeLogo(logoType: String, size: Dp, modifier: Modifier = Modifier) {
    when (logoType) {
        "live" -> LiveLogo(size, modifier)
        "movie" -> ReelLogo(size, modifier)
        "series" -> ClapperLogo(size, modifier)
        else -> Icon(
            Icons.Filled.GridView,
            contentDescription = "4 Screen",
            tint = HomeAccent,
            modifier = modifier.size(size),
        )
    }
}

@Composable
private fun LiveLogo(logoSize: Dp, modifier: Modifier = Modifier) {
    // v2.12: matches the AZUL reference — a pink broadcast antenna with
    // radiating signal waves above a white LIVE sign. (The old white TV
    // set did not match the reference.)
    Box(modifier = modifier.size(logoSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            // Soft pink glow behind the antenna.
            drawCircle(
                Color(0xFFE91E63).copy(alpha = 0.22f),
                radius = w * 0.42f,
                center = Offset(cx, h * 0.28f),
            )
            // Antenna mast + tip dot.
            val mastTop = h * 0.06f
            val mastBottom = h * 0.46f
            val mastW = (w * 0.025f).coerceAtLeast(2f)
            drawLine(Color(0xFFFF7AA2), Offset(cx, mastTop), Offset(cx, mastBottom), strokeWidth = mastW)
            drawCircle(Color(0xFFE91E63), radius = w * 0.05f, center = Offset(cx, mastTop))
            // Signal waves radiating from the mast (pink arcs, 3 each side).
            val waveCenter = Offset(cx, h * 0.30f)
            val arcW = (w * 0.03f).coerceAtLeast(2f)
            for (i in 1..3) {
                val r = w * (0.12f + i * 0.08f)
                val arcTopLeft = Offset(waveCenter.x - r, waveCenter.y - r)
                val arcSize = Size(r * 2f, r * 2f)
                drawArc(Color(0xFFFF7AA2), -55f, 110f, false, arcTopLeft, arcSize, style = Stroke(width = arcW))
                drawArc(Color(0xFFFF7AA2), 115f, 110f, false, arcTopLeft, arcSize, style = Stroke(width = arcW))
            }
            // White LIVE sign box.
            drawRoundRect(
                Color.White,
                topLeft = Offset(w * 0.14f, h * 0.56f),
                size = Size(w * 0.72f, h * 0.32f),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            )
        }
        Text(
            "LIVE",
            color = Color(0xFFE91E63),
            fontWeight = FontWeight.Bold,
            fontSize = (logoSize.value * 0.20f).sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = (logoSize.value * 0.60f).dp),
        )
    }
}

@Composable
private fun ReelLogo(logoSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(logoSize)) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h * 0.44f)
        val r = minOf(w, h) * 0.40f
        // White reel.
        drawCircle(Color.White, radius = r, center = c)
        // 5 holes in a ring + centre hole (dark, like the reference reel).
        val holeR = r * 0.17f
        val ringR = r * 0.58f
        for (i in 0 until 5) {
            val a = (-90f + i * 72f) * PI.toFloat() / 180f
            drawCircle(
                Color(0xFF3A3648),
                radius = holeR,
                center = Offset(c.x + ringR * cos(a), c.y + ringR * sin(a)),
            )
        }
        drawCircle(Color(0xFF3A3648), radius = r * 0.20f, center = c)
        // Purple film strip curling at the bottom.
        val sw = r * 1.7f
        val sh = r * 0.46f
        val sx = c.x - sw / 2f
        val sy = c.y + r * 0.52f
        drawRoundRect(
            HomeAccent,
            topLeft = Offset(sx, sy),
            size = Size(sw, sh),
            cornerRadius = CornerRadius(sh * 0.25f, sh * 0.25f),
        )
        val pw = sw / 9f
        for (i in 0 until 3) {
            drawRoundRect(
                Color.White,
                topLeft = Offset(sx + pw * (1.2f + i * 3f), sy + sh * 0.22f),
                size = Size(pw, sh * 0.56f),
                cornerRadius = CornerRadius(2f, 2f),
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
        // Purple body.
        drawRoundRect(
            HomeAccent,
            topLeft = Offset(bx, by),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
        )
        // Top slate bar (deeper purple) with white diagonal stripes.
        val barH = h * 0.20f
        val barTop = by - barH * 0.55f
        drawRoundRect(
            Color(0xFF9333EA),
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
            containerColor = HomeSurface,
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
