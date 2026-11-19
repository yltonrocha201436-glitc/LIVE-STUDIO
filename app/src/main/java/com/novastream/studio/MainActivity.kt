package com.novastream.studio

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Gold = Color(0xFFD6B15E)
private val Dark = Color(0xFF090909)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NovaScreen() }
    }
}

@Composable
private fun NovaScreen() {
    val context = LocalContext.current
    var cameraOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var audioOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        cameraOk = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        audioOk = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) {
        val missing = mutableListOf<String>()
        if (!cameraOk) missing.add(Manifest.permission.CAMERA)
        if (!audioOk) missing.add(Manifest.permission.RECORD_AUDIO)
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    var front by remember { mutableStateOf(false) }
    var scene by remember { mutableStateOf("Câmera") }
    var title by remember { mutableStateOf("SEU NOME | AO VIVO") }
    var capture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Pronto para gravar") }

    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, background = Dark, surface = Color(0xFF191919))) {
        Column(
            Modifier.fillMaxSize().background(Dark).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NOVASTREAM", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Black)
                    Text("STUDIO", color = Color.White, letterSpacing = 5.sp, fontSize = 11.sp)
                }
                Text(if (running) "● GRAVANDO" else "● ESTÚDIO", color = if (running) Color.Red else Gold)
            }
            Box(Modifier.fillMaxWidth().aspectRatio(9f / 12f).clip(RoundedCornerShape(18.dp)).background(Color.Black)) {
                if (cameraOk && scene == "Câmera") {
                    CameraFeed(front, { capture = it }, Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (!cameraOk) "LIBERE A CÂMERA" else if (scene == "Intervalo") "JÁ VOLTAMOS" else "CENA PRETA",
                            color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (title.isNotBlank()) {
                    Text(title, Modifier.align(Alignment.BottomStart).padding(12.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xBB000000)).padding(8.dp),
                        color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text("PRÉVIA", Modifier.align(Alignment.TopStart).padding(10.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(6.dp),
                    color = Color.White, fontSize = 10.sp)
            }
            Text("CENAS", color = Gold, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Câmera", "Intervalo", "Preto").forEach { value ->
                    Button(onClick = { scene = value }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (scene == value) Gold else Color.DarkGray)) {
                        Text(value, color = if (scene == value) Color.Black else Color.White, fontSize = 11.sp)
                    }
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it },
                label = { Text("Texto na prévia") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { front = !front; scene = "Câmera" }, modifier = Modifier.weight(1f)) {
                    Text("Virar câmera")
                }
                OutlinedButton(onClick = {
                    val missing = mutableListOf<String>()
                    if (!cameraOk) missing.add(Manifest.permission.CAMERA)
                    if (!audioOk) missing.add(Manifest.permission.RECORD_AUDIO)
                    if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
                }, modifier = Modifier.weight(1f)) { Text("Permissões") }
            }
            Button(
                onClick = {
                    val active = recording
                    if (active != null) {
                        active.stop()
                        status = "Finalizando arquivo..."
                    } else {
                        val video = capture
                        if (!cameraOk) {
                            permissions.launch(arrayOf(Manifest.permission.CAMERA))
                        } else if (video == null || scene != "Câmera") {
                            status = "Selecione Câmera e aguarde a prévia"
                        } else {
                            try {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                val values = ContentValues().apply {
                                    put(MediaStore.Video.Media.DISPLAY_NAME, "NovaStream_" + stamp + ".mp4")
                                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NovaStreamStudio")
                                }
                                val output = MediaStoreOutputOptions.Builder(context.contentResolver,
                                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
                                var pending = video.output.prepareRecording(context, output)
                                if (audioOk) pending = pending.withAudioEnabled()
                                recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                                    when (event) {
                                        is VideoRecordEvent.Start -> {
                                            running = true
                                            status = if (audioOk) "Gravando vídeo e microfone" else "Gravando vídeo sem áudio"
                                        }
                                        is VideoRecordEvent.Finalize -> {
                                            recording = null
                                            running = false
                                            status = if (event.hasError()) "Falha ao salvar: " + event.error
                                                else "Salvo em Filmes/NovaStreamStudio"
                                            Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            } catch (error: Exception) {
                                status = "Erro: " + (error.localizedMessage ?: "não foi possível iniciar")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (running) Color(0xFFB3261E) else Gold),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (running) "PARAR GRAVAÇÃO" else "● GRAVAR VÍDEO", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(status, color = Color.LightGray, fontSize = 12.sp)
            Text(
                "Protótipo 0.1.0: prévia da câmera, troca frontal/traseira e gravação MP4 com áudio do microfone quando autorizado. O texto aparece somente na prévia. Ainda não há transmissão ao vivo, chroma key, captura de tela ou mixer de áudio.",
                color = Color.Gray, fontSize = 10.sp, lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun CameraFeed(
    front: Boolean,
    onReady: (VideoCapture<Recorder>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val owner: LifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val recorder = remember {
        Recorder.Builder().setQualitySelector(
            QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
        ).build()
    }
    val video = remember { VideoCapture.withOutput(recorder) }
    AndroidView(factory = { previewView }, modifier = modifier)

    LaunchedEffect(front, owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val lens = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(owner, lens, preview, video)
                onReady(video)
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(owner) {
        onDispose {
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) { }
        }
    }
}
