package app.nightplayer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Snap(
    val playing: Boolean,
    val title: String,
    val artist: String?,
    val art: ByteArray?,
    val index: Int,
    val count: Int,
    val position: Long,
    val duration: Long
)

private fun snap(c: MediaController?): Snap {
    if (c == null) return Snap(false, "", null, null, 0, 0, 0L, 0L)
    val md = c.mediaMetadata
    val title = (md.title ?: c.currentMediaItem?.mediaMetadata?.title ?: "").toString()
    val artist = (md.artist ?: md.albumArtist)?.toString()
    val d = c.duration
    val dur = if (d == C.TIME_UNSET || d < 0L) 0L else d
    return Snap(c.playWhenReady, title, artist, md.artworkData, c.currentMediaItemIndex, c.mediaItemCount, c.currentPosition.coerceAtLeast(0L), dur)
}

private fun decodeArt(bytes: ByteArray): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 300) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (e: Exception) {
    null
}

suspend fun loadFolder(context: Context, c: MediaController, tree: Uri, startId: String?, autoplay: Boolean): Boolean {
    val tracks = withContext(Dispatchers.IO) {
        try {
            scanFolder(context, tree)
        } catch (e: Exception) {
            emptyList()
        }
    }
    if (tracks.isEmpty()) return false
    val rank = Store.order(tree.toString()).withIndex().associate { it.value to it.index }
    val ordered = tracks.sortedWith(compareBy<Track> { rank[it.uri.toString()] ?: Int.MAX_VALUE })
    val items = ordered.map {
        MediaItem.Builder()
            .setMediaId(it.uri.toString())
            .setUri(it.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(it.name).build())
            .build()
    }
    val index = items.indexOfFirst { it.mediaId == startId }.coerceAtLeast(0)
    c.setMediaItems(items, index, Store.position(items[index].mediaId))
    c.prepare()
    if (autoplay) c.play()
    return true
}

@Composable
fun PlayerScreen(controller: MediaController?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                tick++
            }
        }
        controller?.addListener(l)
        onDispose { controller?.removeListener(l) }
    }
    LaunchedEffect(controller) {
        while (true) {
            delay(500)
            tick++
        }
    }
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        val f = Store.folder.value
        if (c.mediaItemCount == 0 && f != null) loadFolder(context, c, Uri.parse(f), Store.lastTrack, false)
    }
    val s = remember(tick, controller) { snap(controller) }
    val folder by Store.folder.collectAsState()
    val speed by Store.speed.collectAsState()
    val sleep by Store.sleepMinutes.collectAsState()
    val fade by Store.fadeMinutes.collectAsState()
    val pauseAtEnd by Store.pauseAtEnd.collectAsState()
    val timer by Store.timer.collectAsState()
    val folderLabel = remember(folder) { folder?.let { folderName(context, Uri.parse(it)) } ?: "" }
    var dialog by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var pending by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
            }
            Store.setFolder(uri.toString())
            message = "Loading…"
            pending = uri
        }
    }
    LaunchedEffect(controller, pending) {
        val c = controller ?: return@LaunchedEffect
        val uri = pending ?: return@LaunchedEffect
        pending = null
        scope.launch {
            message = if (loadFolder(context, c, uri, null, true)) null else "No audio files in that folder"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.bg)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Text(
                folderLabel,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                color = Palette.muted,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = { picker.launch(null) },
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = 12.dp)
            ) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = "Choose folder", tint = Palette.text)
            }
        }
        val c = controller
        if (c != null && s.count == 0) {
            EmptyState(message) { picker.launch(null) }
        } else if (c != null) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                NowPlaying(c, s) { dialog = "tracks" }
                val t = timer
                if (t != null) TimerCard(t)
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingChip(Icons.Filled.Speed, "Speed", formatSpeed(speed), speed != 1f, Modifier.weight(1f)) { dialog = "speed" }
                    SettingChip(Icons.Filled.Bedtime, "Sleep timer", if (sleep > 0) "$sleep min" else "Off", sleep > 0, Modifier.weight(1f)) { dialog = "sleep" }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingChip(Icons.AutoMirrored.Filled.VolumeDown, "Fade out", if (fade > 0) "$fade min" else "Off", fade > 0 && sleep > 0, Modifier.weight(1f)) { dialog = "fade" }
                    SettingChip(
                        if (pauseAtEnd) Icons.Filled.PauseCircleOutline else Icons.Filled.SkipNext,
                        "End of track",
                        if (pauseAtEnd) "Pause" else "Continue",
                        pauseAtEnd,
                        Modifier.weight(1f)
                    ) { Store.setPauseAtEnd(!pauseAtEnd) }
                }
            }
        }
    }

    when (dialog) {
        "speed" -> SpeedDialog(speed) { dialog = null }
        "sleep" -> OptionDialog(
            "Sleep timer",
            "Playing time before the fade starts. Counts across chapters and pauses with playback.",
            listOf(0, 15, 30, 45, 60, 90, 120),
            sleep,
            { if (it == 0) "Off" else "$it min" },
            { Store.setSleepMinutes(it); dialog = null },
            { dialog = null }
        )
        "fade" -> OptionDialog(
            "Fade out",
            "How long the volume takes to fade to silence after the sleep timer ends.",
            listOf(0, 5, 10, 15, 20, 30),
            fade,
            { if (it == 0) "Off (stop immediately)" else "$it min" },
            { Store.setFadeMinutes(it); dialog = null },
            { dialog = null }
        )
        "tracks" -> {
            val c = controller
            val f = folder
            if (c != null && f != null) TrackDialog(c, f) { dialog = null } else dialog = null
        }
    }
}

@Composable
private fun NowPlaying(c: MediaController, s: Snap, onOpenList: () -> Unit) {
    val art = remember(s.art) { s.art?.let { decodeArt(it) } }
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)).background(Palette.track),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Filled.Book, contentDescription = null, tint = Palette.faint, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(s.title, color = Color.White, fontSize = 22.sp, lineHeight = 28.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val artist = s.artist
            if (!artist.isNullOrBlank()) {
                Text(artist, color = Palette.muted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Row(
                Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenList).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Track ${s.index + 1} of ${s.count}", color = Palette.faint, fontSize = 13.sp)
                Icon(Icons.Filled.ExpandMore, contentDescription = "Show tracks", tint = Palette.faint, modifier = Modifier.size(18.dp))
            }
        }
    }

    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = s.duration
    val frac = if (dur > 0L) (s.position.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Slider(
        value = drag ?: frac,
        onValueChange = { drag = it },
        onValueChangeFinished = {
            val d = drag
            if (d != null && dur > 0L) c.seekTo((d * dur).toLong())
            drag = null
        },
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        colors = SliderDefaults.colors(
            thumbColor = Palette.accent,
            activeTrackColor = Palette.accent,
            inactiveTrackColor = Palette.track
        )
    )
    Row(Modifier.fillMaxWidth()) {
        val shown = drag?.let { (it * dur).toLong() } ?: s.position
        Text(formatTime(shown), color = Palette.muted, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(formatTime(dur), color = Palette.muted, fontSize = 14.sp)
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { c.seekToPreviousMediaItem() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = Palette.text, modifier = Modifier.size(34.dp))
        }
        JumpButton(15, false) { c.seekTo((c.currentPosition - 15_000L).coerceAtLeast(0L)) }
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Palette.accent)
                .clickable {
                    if (c.playWhenReady) {
                        c.pause()
                    } else {
                        if (c.playbackState == Player.STATE_IDLE) c.prepare()
                        if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0L)
                        c.play()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (s.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (s.playing) "Pause" else "Play",
                tint = Palette.onAccent,
                modifier = Modifier.size(44.dp)
            )
        }
        JumpButton(30, true) {
            val d = c.duration
            val target = c.currentPosition + 30_000L
            c.seekTo(if (d != C.TIME_UNSET && d > 0L) target.coerceAtMost(d) else target)
        }
        IconButton(onClick = { c.seekToNextMediaItem() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = Palette.text, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun JumpButton(seconds: Int, forward: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Replay,
            contentDescription = if (forward) "Forward $seconds seconds" else "Back $seconds seconds",
            tint = Palette.text,
            modifier = Modifier.size(40.dp).graphicsLayer { scaleX = if (forward) -1f else 1f }
        )
        Text("$seconds", color = Palette.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TimerCard(t: TimerStatus) {
    val hasFade = t.stopAtMs > t.fadeAtMs
    val line1 = when {
        !hasFade -> "Stops in ${formatTime(t.stopAtMs - t.elapsedMs)}"
        t.elapsedMs < t.fadeAtMs -> "Fading starts in ${formatTime(t.fadeAtMs - t.elapsedMs)}"
        else -> "Fading out"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.card)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Bedtime, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(line1, color = Palette.text, fontSize = 15.sp)
            if (hasFade) {
                Text("Silent and paused in ${formatTime(t.stopAtMs - t.elapsedMs)}", color = Palette.muted, fontSize = 13.sp)
            }
            Box(
                Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.track)
            ) {
                Box(
                    Modifier.fillMaxHeight()
                        .fillMaxWidth((t.elapsedMs.toFloat() / t.stopAtMs.coerceAtLeast(1L)).coerceIn(0f, 1f))
                        .background(Palette.accent)
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = { Store.commands.tryEmit(TimerCommand.Restart) }) { Text("Restart") }
            TextButton(onClick = { Store.commands.tryEmit(TimerCommand.Cancel) }) { Text("Cancel") }
        }
    }
}

@Composable
private fun SettingChip(icon: ImageVector, label: String, value: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (active) Palette.cardOn else Color.Transparent)
            .border(1.dp, if (active) Palette.cardOn else Palette.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Palette.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, color = Palette.muted, fontSize = 12.sp)
            Text(value, color = Palette.text, fontSize = 15.sp)
        }
    }
}

@Composable
private fun EmptyState(message: String?, onPick: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = Palette.muted, modifier = Modifier.size(56.dp))
        Text(
            message ?: "Choose a folder of audio files to start",
            color = Palette.muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )
        Button(
            onClick = onPick,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Palette.onAccent)
        ) { Text("Choose folder") }
    }
}

@Composable
private fun OptionDialog(
    title: String,
    note: String,
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = {
            Column {
                Text(note, color = Palette.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                options.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onSelect(o) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = o == selected, onClick = { onSelect(o) })
                        Text(label(o), color = Palette.text, fontSize = 16.sp)
                    }
                }
            }
        }
    )
}

@Composable
private fun SpeedDialog(speed: Float, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Playback speed") },
        text = {
            Column {
                Text("Stays the same for every track until you change it.", color = Palette.muted, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { Store.setSpeed(roundSpeed(speed - 0.05f)) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Slower", tint = Palette.text)
                    }
                    Text(formatSpeed(speed), color = Palette.text, fontSize = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { Store.setSpeed(roundSpeed(speed + 0.05f)) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Faster", tint = Palette.text)
                    }
                }
                Slider(
                    value = speed,
                    onValueChange = { Store.setSpeed(roundSpeed(it)) },
                    valueRange = 0.5f..2f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = Palette.accent,
                        activeTrackColor = Palette.accent,
                        inactiveTrackColor = Palette.track
                    )
                )
                Row(Modifier.fillMaxWidth()) {
                    listOf(0.75f, 0.85f, 1f, 1.25f).forEach { v ->
                        TextButton(onClick = { Store.setSpeed(v) }, modifier = Modifier.weight(1f)) {
                            Text(formatSpeed(v), color = if (v == speed) Palette.accent else Palette.muted)
                        }
                    }
                }
            }
        }
    )
}


private class Entry(val id: String, val name: String)

@Composable
private fun TrackDialog(c: MediaController, folder: String, onDismiss: () -> Unit) {
    val entries = remember(c) {
        mutableStateListOf<Entry>().apply {
            for (i in 0 until c.mediaItemCount) {
                val item = c.getMediaItemAt(i)
                add(Entry(item.mediaId, item.mediaMetadata.title?.toString() ?: "Track ${i + 1}"))
            }
        }
    }
    val currentId = c.currentMediaItem?.mediaId
    val rowPx = with(LocalDensity.current) { 52.dp.toPx() }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragStart by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    fun finishDrag() {
        val to = dragIndex
        if (to != null && to != dragStart) {
            c.moveMediaItem(dragStart, to)
            Store.saveOrder(folder, entries.map { it.id })
        }
        dragIndex = null
        dragOffset = 0f
    }
    fun sortByName() {
        val sorted = entries.sortedWith { a, b -> naturalCompare(a.name, b.name) }
        sorted.forEachIndexed { target, e ->
            val from = entries.indexOfFirst { it.id == e.id }
            if (from != target) {
                c.moveMediaItem(from, target)
                entries.add(target, entries.removeAt(from))
            }
        }
        Store.saveOrder(folder, null)
    }
    val initial = remember(c) { (entries.indexOfFirst { it.id == currentId } - 3).coerceAtLeast(0) }
    val scroll = rememberScrollState(initial * rowPx.toInt())
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = { sortByName() }) { Text("Sort by name") } },
        title = { Text("Tracks") },
        text = {
            Column {
                Text("Tap a track to play it. Drag the handle to reorder.", color = Palette.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(scroll)) {
                    entries.forEachIndexed { i, e ->
                        key(e.id) {
                            val dragged = dragIndex == i
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .zIndex(if (dragged) 1f else 0f)
                                    .graphicsLayer { translationY = if (dragged) dragOffset else 0f }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (dragged) Palette.cardOn else Color.Transparent),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            val idx = entries.indexOfFirst { it.id == e.id }
                                            if (idx >= 0) {
                                                c.seekTo(idx, 0L)
                                                c.play()
                                            }
                                            onDismiss()
                                        }
                                        .padding(start = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        e.name,
                                        color = if (e.id == currentId) Palette.accent else Palette.text,
                                        fontWeight = if (e.id == currentId) FontWeight.Medium else FontWeight.Normal,
                                        fontSize = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = "Move",
                                    tint = Palette.faint,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .pointerInput(e.id) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    val idx = entries.indexOfFirst { it.id == e.id }
                                                    dragIndex = idx
                                                    dragStart = idx
                                                    dragOffset = 0f
                                                },
                                                onDragEnd = { finishDrag() },
                                                onDragCancel = { finishDrag() },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragOffset += amount.y
                                                    val idx = dragIndex
                                                    if (idx != null) {
                                                        if (dragOffset > rowPx / 2 && idx < entries.lastIndex) {
                                                            entries.add(idx + 1, entries.removeAt(idx))
                                                            dragIndex = idx + 1
                                                            dragOffset -= rowPx
                                                        } else if (dragOffset < -rowPx / 2 && idx > 0) {
                                                            entries.add(idx - 1, entries.removeAt(idx))
                                                            dragIndex = idx - 1
                                                            dragOffset += rowPx
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        .padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
