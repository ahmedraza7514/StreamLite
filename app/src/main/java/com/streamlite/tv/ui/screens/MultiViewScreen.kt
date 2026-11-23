package com.streamlite.tv.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.ui.PlayerView
import com.streamlite.tv.data.DataRepository
import com.streamlite.tv.data.LiveStream
import com.streamlite.tv.data.LoginSession
import com.streamlite.tv.data.LoginType
import com.streamlite.tv.data.XtreamClient
import com.streamlite.tv.player.PlayerFactory
import com.streamlite.tv.ui.theme.SlAccent
import com.streamlite.tv.ui.theme.SlBackground
import com.streamlite.tv.ui.theme.SlText
import com.streamlite.tv.ui.theme.SlTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 4-Screen multiview: pick 4 live channels, watch them all at once in a 2x2 grid.
 *
 * 2026-11-03 (Ahmed Bhai's annotated screenshot):
 * - Tap a quadrant -> pauses/plays THAT quadrant independently.
 * - Speaker icon on the side of each quadrant -> mute/unmute (the audible
 *   quadrant switches here too; only one quadrant has audio at a time).
 * - Players are built by PlayerFactory (tuned buffers + 720p cap) so the
 *   2GB TV box doesn't choke on 4 simultaneous streams.
 */
@Composable
fun MultiViewScreen(
    session: LoginSession,
) {
    var selected by remember { mutableStateOf<List<LiveStream>>(emptyList()) }
    var started by remember { mutableStateOf(false) }

    // v2.12: back is owned by the single global handler in MainActivity —
    // back from 4-screen returns to the last screen/category, never closes
    // the whole app.

    if (!started) {
        ChannelPicker(
            session = session,
            selected = selected,
            onToggle = { ch ->
                selected = if (selected.contains(ch)) {
                    selected - ch
                } else if (selected.size < 4) {
                    selected + ch
                } else {
                    selected
                }
            },
            onStart = { started = true },
        )
    } else {
        QuadGrid(channels = selected.take(4), session = session)
    }
}

@Composable
private fun ChannelPicker(
    session: LoginSession,
    selected: List<LiveStream>,
    onToggle: (LiveStream) -> Unit,
    onStart: () -> Unit,
) {
    // 2026-11-04 (Batch 2): on the TV box DataRepository.liveStreams can be
    // null/empty when the picker opens (search found nothing, nothing could
    // be added). Fall back to a direct Xtream fetch so the picker always has
    // channels — same bulk call LiveTvScreen uses.
    var channels by remember { mutableStateOf(DataRepository.liveStreams ?: emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (channels.isEmpty() && session.type == LoginType.XTREAM) {
            try {
                val client = XtreamClient(session.server, session.username, session.password)
                channels = withContext(Dispatchers.IO) { client.liveStreamsAll() }
            } catch (e: Exception) {
                loadError = e.message ?: "Could not load channels."
            }
        }
    }
    var query by remember { mutableStateOf("news") }
    // 2026-11-13 (v2.20.0, TV rule): 4 channels select ho jayen to
    // remote ki selection KHUD "Start Watching" button par jaye —
    // channels ki list me wapis nahi (Ahmed Bhai ka hukum).
    val startReq = remember { FocusRequester() }
    LaunchedEffect(selected.size) {
        if (selected.size == 4) {
            try { startReq.requestFocus() } catch (_: Exception) { /* no-op */ }
        }
    }
    // 2026-11-05 (Batch 8, Point 4): separate OVERALL category search below
    // the channel search — filters channels by their category name.
    var catQuery by remember { mutableStateOf("") }
    // 2026-11-23 (v2.22.0): categories ab state me hain — agar sync
    // se na ayi hon to neeche wala fallback khud le ayega.
    var categories by remember { mutableStateOf(DataRepository.liveCategories ?: emptyList()) }
    LaunchedEffect(Unit) {
        if (categories.isEmpty() && session.type == LoginType.XTREAM) {
            try {
                val client = XtreamClient(session.server, session.username, session.password)
                categories = withContext(Dispatchers.IO) { client.liveCategories() }
            } catch (_: Exception) {
                // Dropdown me sirf "All Channels" rahega — koi crash nahi.
            }
        }
    }
    // 2026-11-23 (v2.22.0, Bukhari sb ki requirement): CATEGORY
    // DROPDOWN — "All Channels" ya koi ek category chuno, list
    // sirf usi ke channels dikhaye gi. Categories playlist/source
    // se khud ati hain (hardcode nahi). Manual search barqarar.
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    var catMenuOpen by remember { mutableStateOf(false) }
    val filtered = remember(query, catQuery, channels, categories, selectedCatId) {
        var result = channels
        if (selectedCatId != null) {
            result = result.filter { it.categoryId == selectedCatId }
        }
        if (query.isNotBlank()) {
            result = result.filter { it.name.contains(query, ignoreCase = true) }
        }
        if (catQuery.isNotBlank()) {
            val catIds = categories.filter { it.name.contains(catQuery, ignoreCase = true) }
                .map { it.id }.toSet()
            result = result.filter { it.categoryId in catIds }
        }
        result
    }

    Column(
        modifier = Modifier.fillMaxSize().background(SlBackground).padding(24.dp),
    ) {
        Text(
            "4 Screen — Pick 4 Channels",
            style = MaterialTheme.typography.headlineSmall,
            color = SlText,
        )
        Text(
            "${selected.size} / 4 selected — tap channels to pick",
            fontSize = 14.sp,
            color = SlTextDim,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        // Search
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1E2E), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = SlText, fontSize = 15.sp),
            singleLine = true,
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Search channels...", color = SlTextDim, fontSize = 14.sp)
                inner()
            },
        )
        Spacer(Modifier.height(12.dp))

        // 2026-11-23 (v2.22.0): CATEGORY DROPDOWN button — dabane
        // par categories ki list khulti hai; ek chuno to channel
        // list sirf usi category ki reh jati hai.
        var catBtnFocused by remember { mutableStateOf(false) }
        val selectedCatName = categories.firstOrNull { it.id == selectedCatId }?.name
        Card(
            onClick = { catMenuOpen = !catMenuOpen },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { catBtnFocused = it.isFocused }
                .border(
                    width = if (catBtnFocused) 3.dp else 0.dp,
                    color = if (catBtnFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0F1E2E),
                contentColor = SlText,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Category:", fontSize = 15.sp, color = SlTextDim)
                Spacer(Modifier.width(8.dp))
                Text(
                    selectedCatName ?: "All Channels",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(if (catMenuOpen) "▴" else "▾", fontSize = 15.sp, color = SlText)
            }
        }
        if (catMenuOpen) {
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    CategoryMenuRow(
                        label = "All Channels",
                        isSel = selectedCatId == null,
                        onClick = { selectedCatId = null; catMenuOpen = false },
                    )
                }
                items(categories, key = { it.id }) { cat ->
                    CategoryMenuRow(
                        label = cat.name,
                        isSel = selectedCatId == cat.id,
                        onClick = { selectedCatId = cat.id; catMenuOpen = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Overall category search (separate field, below the channel search)
        androidx.compose.foundation.text.BasicTextField(
            value = catQuery,
            onValueChange = { catQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1E2E), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(color = SlText, fontSize = 15.sp),
            singleLine = true,
            decorationBox = { inner ->
                if (catQuery.isEmpty()) Text("Search category...", color = SlTextDim, fontSize = 14.sp)
                inner()
            },
        )
        Spacer(Modifier.height(12.dp))

        if (loadError != null && channels.isEmpty()) {
            Text(
                loadError!!,
                fontSize = 14.sp,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.streamId }) { ch ->
                val isSel = selected.contains(ch)
                // 2026-11-05 (Batch 8, Point 4): WHITE 3dp border where the
                // remote selection lands — visible from far, like other screens.
                var rowFocused by remember { mutableStateOf(false) }
                Card(
                    onClick = { onToggle(ch) },
                    modifier = Modifier
                        .onFocusChanged { rowFocused = it.isFocused }
                        // v2.20.0: 4 select ho jayen to ghair-
                        // muntakhib channels par selection na jaye
                        // (sirf chune hue ko hatta sakte hain).
                        .focusProperties { canFocus = selected.size < 4 || isSel }
                        .border(
                            width = if (rowFocused) 3.dp else 0.dp,
                            color = if (rowFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        ),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) SlAccent else Color(0xFF0F1E2E),
                        contentColor = if (isSel) Color.Black else SlText,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (isSel) "✓ " else "",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(ch.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onStart,
            enabled = selected.size == 4,
            modifier = Modifier.focusRequester(startReq).fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SlAccent,
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFF333333),
                disabledContentColor = SlTextDim,
            ),
        ) {
            Text(
                if (selected.size == 4) "Start Watching" else "Pick ${4 - selected.size} more",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** v2.22.0: category dropdown ki ek row — select shuda par ✓ aur accent rang. */
@Composable
private fun CategoryMenuRow(label: String, isSel: Boolean, onClick: () -> Unit) {
    var rowFocused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { rowFocused = it.isFocused }
            .border(
                width = if (rowFocused) 3.dp else 0.dp,
                color = if (rowFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSel) SlAccent else Color(0xFF0F1E2E),
            contentColor = if (isSel) Color.Black else SlText,
        ),
    ) {
        Text(
            (if (isSel) "✓ " else "") + label,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun QuadGrid(channels: List<LiveStream>, session: LoginSession) {
    val context = LocalContext.current
    val client = remember { XtreamClient(session.server, session.username, session.password) }
    // One tuned player per quadrant (PlayerFactory: small buffers + 720p cap
    // so 4 streams don't OOM the 2GB TV box)
    val players = remember {
        List(4) { idx ->
            PlayerFactory.create(context).apply {
                val ch = channels.getOrNull(idx)
                if (ch != null) {
                    val url = client.liveHlsUrl(ch.streamId)
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
                // 2026-11-05 (Batch 8 fix): back to ONE audible quadrant at a
                // time (click a screen's speaker to hear THAT one). The 4-players-
                // at-once change is reverted per user instruction.
                // Audio focus is OFF for these players: Android's focus system
                // was ducking/pausing quadrants, so only some of the 4 could
                // ever be heard — the app's own volume control decides now.
                setAudioAttributes(AudioAttributes.DEFAULT, false)
                volume = if (idx == 0) 1f else 0f
            }
        }
    }
    // Which quadrant has audio (null = all muted). Only one at a time.
    var audibleIdx by remember { mutableStateOf<Int?>(0) }
    // 2026-11-13 (v2.20.0, TV rule): remote ki selection ka tay
    // shuda silsila — har box me pehle play/pause, phir speaker,
    // phir agla box. Is ke liye har button ka apna FocusRequester.
    val playReqs = remember { List(4) { FocusRequester() } }
    val speakerReqs = remember { List(4) { FocusRequester() } }
    LaunchedEffect(Unit) {
        try { playReqs[0].requestFocus() } catch (_: Exception) { /* no-op */ }
    }
    // Per-quadrant pause state (independent for each screen)
    val pausedStates = remember { List(4) { mutableStateOf(false) } }

    fun togglePause(idx: Int) {
        val p = players[idx]
        p.playWhenReady = !p.playWhenReady
        pausedStates[idx].value = !p.playWhenReady
    }

    fun toggleMute(idx: Int) {
        audibleIdx = if (audibleIdx == idx) null else idx
        players.forEachIndexed { i, p ->
            p.volume = if (i == audibleIdx) 1f else 0f
        }
    }

    // 2026-11-23 (v2.22.0): FULLSCREEN — kisi ek screen ko bari
    // karna (double-OK / double-click). Player WAHI rehta hai,
    // sirf us ka khana bara ho jata hai — video/awaz restart
    // nahi hoti, koi dobara prepare nahi. Back dabane par
    // bilkul waisa hi 4-screen layout wapis (channels, kis ki
    // awaz hai, pause ki halat — sab barqarar, kyunke players
    // ko haath hi nahi lagta).
    var fullscreenIdx by remember { mutableStateOf<Int?>(null) }
    // Remote ki selection is waqt kis box me hai (aur play/pause
    // par hai ya speaker par) — double-OK pehchanne ke liye.
    var focusedQuad by remember { mutableStateOf(0) }
    var focusedIsSpeaker by remember { mutableStateOf(false) }
    var lastCenterQuad by remember { mutableStateOf(-1) }
    var lastCenterAt by remember { mutableStateOf(0L) }
    var swallowCenterUp by remember { mutableStateOf(false) }
    var lastFullscreen by remember { mutableStateOf(-1) }
    LaunchedEffect(fullscreenIdx) {
        val f = fullscreenIdx
        try {
            if (f != null) {
                lastFullscreen = f
                playReqs[f].requestFocus()
            } else if (lastFullscreen >= 0) {
                // Fullscreen se wapis — selection usi box ke
                // play/pause par wapis jaye jahan se bari hui thi.
                playReqs[lastFullscreen].requestFocus()
            }
        } catch (_: Exception) { /* no-op */ }
    }

    DisposableEffect(Unit) {
        onDispose {
            players.forEach { it.release() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // v2.22.0: poori 4-screen ka markazi key handler —
            // (a) fullscreen me Back = wapis 4-screen (global
            // back handler tak baat jane hi nahi dete);
            // (b) grid me DOUBLE-OK (400ms ke andar dusra OK)
            // = focused wali screen fullscreen. Ek akela OK
            // bilkul pehle jaisa hai (play/pause ya speaker) —
            // galti se fullscreen nahi khulta.
            .onPreviewKeyEvent { ev ->
                if (fullscreenIdx != null) {
                    if (ev.key == Key.Back && ev.type == KeyEventType.KeyDown) {
                        fullscreenIdx = null
                        true
                    } else {
                        false
                    }
                } else {
                    val isCenter = ev.key == Key.DirectionCenter ||
                        ev.key == Key.Enter || ev.key == Key.NumPadEnter
                    if (!isCenter) {
                        false
                    } else if (ev.type == KeyEventType.KeyDown) {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (focusedQuad == lastCenterQuad && now - lastCenterAt < 400) {
                            // DOUBLE-OK pakra gaya: pehle OK ka
                            // asar (pause ya mute jo bhi hua tha)
                            // wapis lo — screen bilkul apni
                            // pehli halat me bari hogi.
                            if (focusedIsSpeaker) toggleMute(focusedQuad) else togglePause(focusedQuad)
                            fullscreenIdx = focusedQuad
                            swallowCenterUp = true
                            lastCenterAt = 0L
                            true
                        } else {
                            lastCenterQuad = focusedQuad
                            lastCenterAt = now
                            false
                        }
                    } else if (ev.type == KeyEventType.KeyUp && swallowCenterUp) {
                        swallowCenterUp = false
                        true
                    } else {
                        false
                    }
                }
            },
    ) {
        if (fullscreenIdx != null) {
            val f = fullscreenIdx!!
            QuadPlayer(
                player = players[f],
                title = channels.getOrNull(f)?.name ?: "",
                isAudible = audibleIdx == f,
                isPaused = pausedStates[f].value,
                onTap = { togglePause(f) },
                onToggleMute = { toggleMute(f) },
                idx = f,
                playReqs = playReqs,
                speakerReqs = speakerReqs,
                singleMode = true,
                onFocusControl = { isSpk -> focusedQuad = f; focusedIsSpeaker = isSpk },
                onDoubleTap = { },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top row: 2 quadrants
        Row(modifier = Modifier.weight(1f)) {
            QuadPlayer(
                player = players[0],
                title = channels.getOrNull(0)?.name ?: "",
                isAudible = audibleIdx == 0,
                isPaused = pausedStates[0].value,
                onTap = { togglePause(0) },
                onToggleMute = { toggleMute(0) },
                idx = 0,
                playReqs = playReqs,
                speakerReqs = speakerReqs,
                onFocusControl = { isSpk -> focusedQuad = 0; focusedIsSpeaker = isSpk },
                onDoubleTap = { fullscreenIdx = 0 },
                modifier = Modifier.weight(1f),
            )
            QuadPlayer(
                player = players[1],
                title = channels.getOrNull(1)?.name ?: "",
                isAudible = audibleIdx == 1,
                isPaused = pausedStates[1].value,
                onTap = { togglePause(1) },
                onToggleMute = { toggleMute(1) },
                idx = 1,
                playReqs = playReqs,
                speakerReqs = speakerReqs,
                onFocusControl = { isSpk -> focusedQuad = 1; focusedIsSpeaker = isSpk },
                onDoubleTap = { fullscreenIdx = 1 },
                modifier = Modifier.weight(1f),
            )
        }
        // Bottom row: 2 quadrants
        Row(modifier = Modifier.weight(1f)) {
            QuadPlayer(
                player = players[2],
                title = channels.getOrNull(2)?.name ?: "",
                isAudible = audibleIdx == 2,
                isPaused = pausedStates[2].value,
                onTap = { togglePause(2) },
                onToggleMute = { toggleMute(2) },
                idx = 2,
                playReqs = playReqs,
                speakerReqs = speakerReqs,
                onFocusControl = { isSpk -> focusedQuad = 2; focusedIsSpeaker = isSpk },
                onDoubleTap = { fullscreenIdx = 2 },
                modifier = Modifier.weight(1f),
            )
            QuadPlayer(
                player = players[3],
                title = channels.getOrNull(3)?.name ?: "",
                isAudible = audibleIdx == 3,
                isPaused = pausedStates[3].value,
                onTap = { togglePause(3) },
                onToggleMute = { toggleMute(3) },
                idx = 3,
                playReqs = playReqs,
                speakerReqs = speakerReqs,
                onFocusControl = { isSpk -> focusedQuad = 3; focusedIsSpeaker = isSpk },
                onDoubleTap = { fullscreenIdx = 3 },
                modifier = Modifier.weight(1f),
            )
        }
        // v2.13.2: hint bar REMOVED per user request — no text under the
        // 4 screens anymore. (MultiViewScreen restored to the v2.11/236
        // version: the v2.13/2.13.1 audio changes are reverted.)
    }
        }
    }
}

@Composable
private fun QuadPlayer(
    player: ExoPlayer,
    title: String,
    isAudible: Boolean,
    isPaused: Boolean,
    onTap: () -> Unit,
    onToggleMute: () -> Unit,
    idx: Int,
    playReqs: List<FocusRequester>,
    speakerReqs: List<FocusRequester>,
    // v2.22.0: singleMode = ye box fullscreen me akela hai —
    // parosi boxes ke focus jumps band. onFocusControl =
    // double-OK ki pehchan ke liye (kis box ka konsa button).
    // onDoubleTap = mouse/touch se double-click → fullscreen.
    singleMode: Boolean = false,
    onFocusControl: (Boolean) -> Unit = {},
    onDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var quadFocused by remember { mutableStateOf(false) }
    var speakerFocused by remember { mutableStateOf(false) }
    var playBtnFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .border(
                // 2026-11-04 (Batch 4, Point 6+10): WHITE 3dp border where the
                // D-pad cursor goes — must be clearly visible on the TV box.
                width = when {
                    quadFocused -> 3.dp
                    isAudible -> 3.dp
                    else -> 1.dp
                },
                color = when {
                    quadFocused -> Color.White
                    isAudible -> SlAccent
                    else -> Color(0xFF333333)
                },
                shape = RoundedCornerShape(4.dp),
            )
            .onFocusChanged { quadFocused = it.isFocused }
            // v2.20.0: box khud ab D-pad focus NAHI leta — selection
            // sirf play/pause aur speaker buttons par chalti hai
            // (Ahmed Bhai ka tay shuda silsila). Touch par tap se
            // pause/play waisa hi hai (pointerInput, focus nahi).
            // v2.22.0: double-click/tap = fullscreen bhi hai;
            // akela tap pehle ki tarah play/pause hi hai.
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { onDoubleTap() },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    // Fill the box completely (no black bars)
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Paused indicator (white vector icon — always visible, every device)
        if (isPaused) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xAA000000), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Paused",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        // Title overlay (top-start)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                (if (isAudible) "🔊 " else "") + title,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
        // Speaker mute/unmute button (top-end, black button, white icon)
        Card(
            onClick = onToggleMute,
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black,
                contentColor = Color.White,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = if (speakerFocused) 2.dp else 1.dp,
                color = if (speakerFocused) Color.White else Color(0xFF555555),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .onFocusChanged { speakerFocused = it.isFocused; if (it.isFocused) onFocusControl(true) }
                .focusRequester(speakerReqs[idx])
                // v2.20.0 silsila: speaker se AAGE (Right/Down) =
                // agle box ka play/pause; PECHHE (Left/Up) = isi
                // box ka play/pause.
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (singleMode) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            if (idx < 3) {
                                playReqs[idx + 1].requestFocus()
                                true
                            } else false
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            playReqs[idx].requestFocus()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isAudible) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (isAudible) "Mute" else "Unmute",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // 2026-11-04 (Batch 4, Point 10): play/pause button.
        // 2026-11-13 (v2.20.0): ab ye button HAR WAQT mojood hai
        // (pehle sirf box focus par ata tha) — kyunke remote ki
        // selection ka pehla stop yahi hai: box me pehle
        // play/pause, phir speaker, phir agla box.
        Card(
            onClick = onTap,
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black,
                contentColor = Color.White,
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = if (playBtnFocused) 2.dp else 1.dp,
                color = if (playBtnFocused) Color.White else Color(0xFF555555),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 52.dp)
                .onFocusChanged { playBtnFocused = it.isFocused; if (it.isFocused) onFocusControl(false) }
                .focusRequester(playReqs[idx])
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (singleMode) return@onPreviewKeyEvent false
                    when (ev.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            speakerReqs[idx].requestFocus()
                            true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            if (idx > 0) {
                                speakerReqs[idx - 1].requestFocus()
                                true
                            } else false
                        }
                        else -> false
                    }
                },
        ) {
            Box(
                modifier = Modifier.padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isPaused) "Play" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
