package app.supersax.ui.take

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.supersax.R
import app.supersax.separation.Removal
import app.supersax.ui.components.ScreenScaffold
import app.supersax.ui.components.ScrollColumn
import app.supersax.ui.components.SectionCard
import app.supersax.ui.components.Stepper
import app.supersax.ui.nav.appViewModel
import app.supersax.ui.songs.stemName
import app.supersax.ui.theme.Sax
import kotlin.math.roundToInt

/** Record yourself playing with a song: set-up, camera, count-in, then balance, sync, trim and export. */
@Composable
fun TakeScreen(onBack: () -> Unit) {
    val vm = appViewModel { TakeViewModel(it) }
    val stage by vm.stage.collectAsStateWithLifecycle()
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    when (stage) {
        TakeStage.SETUP -> TakeSetup(vm, onBack)
        TakeStage.CAMERA, TakeStage.RECORDING, TakeStage.FINISHING -> TakeCamera(vm, stage)
        TakeStage.REVIEW -> TakeReview(vm, onBack)
    }
}

private fun ms(t: Long): String { val s = (t / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakeSetup(vm: TakeViewModel, onBack: () -> Unit) {
    val s by vm.setup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) vm.openCamera() else denied = true
    }
    ScreenScaffold(stringResource(R.string.take_title), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.take_intro), style = MaterialTheme.typography.bodyMedium, color = Sax.colors.textSecondary)
            SectionCard(stringResource(R.string.take_song)) {
                if (s.songs.isEmpty()) Text(stringResource(R.string.take_no_songs), style = MaterialTheme.typography.bodyMedium)
                s.songs.forEach { song ->
                    val selected = song.id == s.songId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Sax.colors.accentSoft else Color.Transparent)
                            .clickable { vm.pickSong(song.id) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    ) { Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
                }
                val song = s.songs.firstOrNull { it.id == s.songId }
                if (song != null && song.versions.size > 1) {
                    Text(stringResource(R.string.take_version), style = MaterialTheme.typography.labelLarge, color = Sax.colors.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        song.versions.forEach { (id, removed) ->
                            val label = Removal.of(removed)?.let { stemName(it) } ?: stringResource(R.string.take_original)
                            FilterChip(id == s.versionId, { vm.pickVersion(id) }, { Text(label) })
                        }
                    }
                }
                s.preparing?.let { p ->
                    Text(stringResource(R.string.take_preparing), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (s.songId != null) SectionCard(stringResource(R.string.take_settings)) {
                if (s.bars > 0) Stepper(stringResource(R.string.take_start_bar), s.startBar, vm::setStartBar, 1..s.bars)
                else Text(stringResource(R.string.take_from_start), style = MaterialTheme.typography.bodyMedium)
                Stepper(stringResource(R.string.take_speed), (s.speed * 100).roundToInt(), { vm.setSpeed(it / 100f) }, 50..150, 5) { "$it %" }
                Stepper(stringResource(R.string.take_key), s.semitones, vm::setSemitones, -12..12) { if (it > 0) "+$it" else "$it" }
                Text(stringResource(R.string.take_count_in), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            }
            if (s.songId != null) SectionCard(stringResource(R.string.take_listen_on)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(s.headphones, { vm.setHeadphones(true) }, { Text(stringResource(R.string.take_headphones)) })
                    FilterChip(!s.headphones, { vm.setHeadphones(false) }, { Text(stringResource(R.string.take_speaker)) })
                }
                Text(stringResource(if (s.headphones) R.string.take_headphones_info else R.string.take_speaker_info), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
                Text(stringResource(R.string.take_camera), style = MaterialTheme.typography.labelLarge, color = Sax.colors.textSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(s.front, { vm.setFront(true) }, { Text(stringResource(R.string.take_front)) })
                    FilterChip(!s.front, { vm.setFront(false) }, { Text(stringResource(R.string.take_back)) })
                }
            }
            when (s.error) {
                "camera" -> Text(stringResource(R.string.take_error_camera), color = MaterialTheme.colorScheme.error)
                "decode", "take" -> Text(stringResource(R.string.take_error_take), color = MaterialTheme.colorScheme.error)
            }
            if (denied) Text(stringResource(R.string.take_permissions), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    val ok = perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                    if (ok) vm.openCamera() else launcher.launch(perms)
                },
                enabled = s.ready, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.take_open_camera)) }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TakeCamera(vm: TakeViewModel, stage: TakeStage) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val s by vm.setup.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    var capture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    BackHandler { if (stage == TakeStage.CAMERA) vm.closeCamera() else vm.stop() }

    // Binds the camera (again when switching front / back while framing).
    DisposableEffect(s.front) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            val p = future.get()
            provider = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD, Quality.SD), FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            val vc = VideoCapture.withOutput(recorder)
            val selector = if (s.front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, selector, preview, vc)
                capture = vc
            }.onFailure { capture = null }
        }, ContextCompat.getMainExecutor(context))
        onDispose { capture = null; provider?.unbindAll() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())
        // Count-in: the four clicks shown big.
        if (stage == TakeStage.RECORDING && live.beat in 1..4) {
            Text("${live.beat}", fontSize = 160.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (stage == TakeStage.CAMERA) IconButton(onClick = vm::closeCamera) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            if (stage != TakeStage.CAMERA) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(0xFFE53935)))
                Text("  " + ms(live.elapsedMs) + if (live.beat == 0) "  ·  " + stringResource(R.string.take_song_at, ms(live.songMs)) else "",
                    color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            if (stage == TakeStage.CAMERA) IconButton(onClick = { vm.setFront(!s.front) }) { Icon(Icons.Rounded.Cameraswitch, stringResource(R.string.take_switch_camera), tint = Color.White) }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (stage) {
                TakeStage.CAMERA -> {
                    Text(stringResource(R.string.take_frame_hint), color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
                    val vc = capture
                    RecordButton(recording = false, enabled = vc != null) {
                        vc ?: return@RecordButton
                        vm.beginRecording {
                            runCatching {
                                vc.output.prepareRecording(context, FileOutputOptions.Builder(vm.videoOutput()).build())
                                    .start(ContextCompat.getMainExecutor(context), vm::onVideoEvent)
                            }.getOrNull()
                        }
                    }
                }
                TakeStage.RECORDING -> RecordButton(recording = true, enabled = true) { vm.stop() }
                else -> Text(stringResource(R.string.take_finishing), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (recording) R.string.take_stop else R.string.take_record)
    Box(
        Modifier.size(84.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(if (recording) 32.dp else 64.dp).clip(if (recording) RoundedCornerShape(6.dp) else CircleShape).background(Color(0xFFE53935).copy(alpha = if (enabled) 1f else 0.4f)))
    }
}

@Composable
private fun TakeReview(vm: TakeViewModel, onBack: () -> Unit) {
    val r by vm.review.collectAsStateWithLifecycle()
    val context = LocalContext.current
    BackHandler { vm.again() }
    ScreenScaffold(stringResource(R.string.take_review), onBack = onBack) { padding ->
        ScrollColumn(padding) {
            val out = r.exported
            if (out != null) {
                VideoPlayer(out, r.aspect)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::save, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.take_save)) }
                    FilledTonalButton(onClick = { vm.shareIntent()?.let { context.startActivity(it) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.share)) }
                }
                r.saved?.let { Text(stringResource(R.string.take_saved, it), style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.take_edit_hint), style = MaterialTheme.typography.bodySmall, color = Sax.colors.textSecondary)
            }
            SectionCard(stringResource(R.string.take_balance)) {
                Row { Text(stringResource(R.string.take_music), Modifier.weight(1f)); Text(stringResource(R.string.take_sax)) }
                Slider(r.balance, vm::setBalance)
            }
            SectionCard(stringResource(R.string.take_sync)) {
                Text(
                    when {
                        r.nudgeMs > 0 -> stringResource(R.string.take_music_later, r.nudgeMs)
                        r.nudgeMs < 0 -> stringResource(R.string.take_music_earlier, -r.nudgeMs)
                        else -> stringResource(R.string.take_sync_auto)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(r.nudgeMs.toFloat(), { vm.setNudge((it / 5).roundToInt() * 5) }, valueRange = -300f..300f)
            }
            SectionCard(stringResource(R.string.take_trim)) {
                Text("${ms(r.fromMs)} – ${ms(r.toMs)}", style = MaterialTheme.typography.bodyMedium)
                if (r.durationMs > 1000) RangeSlider(r.fromMs.toFloat()..r.toMs.toFloat(), { v -> vm.setTrim(v.start.toLong(), v.endInclusive.toLong()) }, valueRange = 0f..r.durationMs.toFloat())
            }
            OutlinedButton(onClick = vm::togglePreview, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (r.previewing) R.string.stop else R.string.take_listen))
            }
            r.exportProgress?.let { p ->
                Text(stringResource(R.string.take_making), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            }
            r.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (out == null) Button(onClick = vm::export, enabled = r.exportProgress == null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.take_make))
            }
            OutlinedButton(onClick = vm::again, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.take_again)) }
            Spacer(Modifier.size(24.dp))
        }
    }
}

/** The exported video, tap to play or pause. */
@Composable
private fun VideoPlayer(file: java.io.File, aspect: Float) {
    val context = LocalContext.current
    val player = remember(file) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(Uri.fromFile(file))); repeatMode = Player.REPEAT_MODE_OFF; prepare() } }
    DisposableEffect(player) { onDispose { player.release() } }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        player.addListener(object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying } })
    }
    val label = stringResource(if (playing) R.string.pause else R.string.play)
    Box(
        Modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(0.4f, 2.5f)).clip(RoundedCornerShape(16.dp)).background(Color.Black)
            .clickable(onClickLabel = label) { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } },
    ) {
        AndroidView({ SurfaceView(it).also { v -> player.setVideoSurfaceView(v) } }, Modifier.fillMaxSize())
        if (!playing) Text("▶", fontSize = 56.sp, color = Color.White, modifier = Modifier.align(Alignment.Center))
    }
}
