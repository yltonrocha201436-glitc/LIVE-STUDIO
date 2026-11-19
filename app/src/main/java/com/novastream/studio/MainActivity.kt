package com.novastream.studio

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.pedro.library.view.OpenGlView

private val Gold = Color(0xFFD6B15E)
private val Bg = Color(0xFF090909)
private val Panel = Color(0xFF191919)
private val Muted = Color(0xFFB5B5B5)

class MainActivity : ComponentActivity() {
    var engine: StreamEngine? by mutableStateOf(null)
        private set
    var engineMessage by mutableStateOf("Aguardando permissões de câmera e microfone")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NovaStreamApp(this) }
    }

    fun prepareEngine() {
        if (engine != null) return
        val next = StreamEngine(applicationContext) { message ->
            engineMessage = message
        }
        if (next.prepare()) {
            next.setComposition("Câmera", "YLTON ROCHA  |  AO VIVO", false, 0.72f)
            engine = next
            engineMessage = next.status
        } else {
            engineMessage = next.status
            next.release()
        }
    }

    fun attachPreview(view: OpenGlView) {
        engine?.attachPreview(view)
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        super.onDestroy()
    }
}

private enum class StudioTab(val label: String) {
    LIVE("AO VIVO"),
    SCENES("CENAS"),
    SOURCES("FONTES"),
    AUDIO("ÁUDIO"),
    SETTINGS("AJUSTES")
}

@Composable
private fun NovaStreamApp(activity: MainActivity) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("novastream_studio_preferences", android.content.Context.MODE_PRIVATE)
    }
    var tab by rememberSaveable { mutableStateOf(StudioTab.LIVE) }
    var permissionsOk by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var endpoint by rememberSaveable { mutableStateOf(prefs.getString("endpoint", "") ?: "") }
    var scene by rememberSaveable { mutableStateOf(prefs.getString("scene", "Câmera") ?: "Câmera") }
    var sceneDraftsRaw by rememberSaveable { mutableStateOf(prefs.getString("scene_drafts", "") ?: "") }
    val initialDraft = remember(sceneDraftsRaw, scene) { SceneDraftStore.decode(sceneDraftsRaw)[scene] }
    var caption by rememberSaveable { mutableStateOf(initialDraft?.caption ?: (prefs.getString("caption", "YLTON ROCHA  |  AO VIVO") ?: "YLTON ROCHA  |  AO VIVO")) }
    var caption2 by rememberSaveable { mutableStateOf(initialDraft?.secondCaption ?: (prefs.getString("caption2", "") ?: "")) }
    var caption3 by rememberSaveable { mutableStateOf(initialDraft?.thirdCaption ?: (prefs.getString("caption3", "") ?: "")) }
    var qrContent by rememberSaveable { mutableStateOf("") }
    var imageCount by rememberSaveable { mutableStateOf(0) }
    var customScenesRaw by rememberSaveable { mutableStateOf(prefs.getString("custom_scenes", "") ?: "") }
    val customScenes = remember(customScenesRaw) { customScenesRaw.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(12) }
    var chroma by rememberSaveable { mutableStateOf(initialDraft?.chroma ?: prefs.getBoolean("chroma", false)) }
    var visualFilter by rememberSaveable { mutableStateOf(initialDraft?.visualFilter ?: (prefs.getString("visual_filter", "Normal") ?: "Normal")) }
    var sensitivity by rememberSaveable { mutableFloatStateOf(initialDraft?.sensitivity ?: prefs.getFloat("chroma_sensitivity", 0.72f)) }
    var imagePlacementsRaw by rememberSaveable { mutableStateOf(SceneDraftStore.encodePlacements(initialDraft?.imagePlacements ?: emptyList())) }
    val imagePlacements = remember(imagePlacementsRaw) { SceneDraftStore.decodePlacements(imagePlacementsRaw) }
    var imagePathsRaw by rememberSaveable { mutableStateOf(SceneDraftStore.encodePaths(initialDraft?.imagePaths ?: emptyList())) }
    val imagePaths = remember(imagePathsRaw) { SceneDraftStore.decodePaths(imagePathsRaw) }
    var selectedImageLayer by rememberSaveable { mutableIntStateOf(0) }
    var micGain by rememberSaveable { mutableFloatStateOf(prefs.getFloat("mic_gain", 1f)) }
    var systemGain by rememberSaveable { mutableFloatStateOf(prefs.getFloat("system_gain", 1f)) }
    var bitrate by rememberSaveable { mutableFloatStateOf(prefs.getFloat("bitrate", 3.5f)) }
    var muted by rememberSaveable { mutableStateOf(prefs.getBoolean("muted", false)) }
    var imageLoaded by remember { mutableStateOf(false) }
    var mouthClosedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mouthOpenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var chromaBackgroundLoaded by remember { mutableStateOf(false) }
    var screenMode by remember { mutableStateOf(false) }

    fun saveSceneImage(bitmap: Bitmap): String? {
        return try {
            val folder = java.io.File(context.filesDir, "scene_media")
            if (!folder.exists() && !folder.mkdirs()) return null
            val file = java.io.File.createTempFile("layer_", ".png", folder)
            java.io.FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    file.delete()
                    return null
                }
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(endpoint, caption, caption2, caption3, scene, chroma, visualFilter, sensitivity, micGain, systemGain, bitrate, muted, customScenesRaw, imagePlacementsRaw) {
        val savedDrafts = SceneDraftStore.decode(sceneDraftsRaw).toMutableMap()
        savedDrafts[scene] = SceneDraft(
            caption = caption,
            secondCaption = caption2,
            thirdCaption = caption3,
            chroma = chroma,
            visualFilter = visualFilter,
            sensitivity = sensitivity,
            imagePlacements = imagePlacements,
            imagePaths = imagePaths
        )
        val encodedDrafts = SceneDraftStore.encode(savedDrafts)
        if (encodedDrafts != sceneDraftsRaw) sceneDraftsRaw = encodedDrafts
        prefs.edit()
            .putString("endpoint", endpoint).putString("caption", caption)
            .putString("caption2", caption2).putString("caption3", caption3)
            .putString("scene", scene).putBoolean("chroma", chroma)
            .putString("visual_filter", visualFilter).putFloat("chroma_sensitivity", sensitivity)
            .putFloat("mic_gain", micGain).putFloat("system_gain", systemGain)
            .putFloat("bitrate", bitrate).putBoolean("muted", muted)
            .putString("custom_scenes", customScenesRaw)
            .putString("scene_drafts", encodedDrafts).apply()
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ProjectionSession.onReady = { projection ->
                activity.engine?.startScreenCapture(projection)
                screenMode = activity.engine?.screenCaptureActive == true
                activity.engineMessage = activity.engine?.status ?: "Captura de tela ativa"
            }
            ProjectionSession.onFailure = { message ->
                activity.engine?.onScreenCaptureEndedBySystem()
                screenMode = false
                activity.engineMessage = message
            }
            ScreenProjectionService.start(context, result.resultCode, data)
        } else {
            activity.engineMessage = "Compartilhamento de tela cancelado"
            screenMode = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsOk = result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.RECORD_AUDIO] == true
        if (permissionsOk) activity.prepareEngine()
        else activity.engineMessage = "Libere câmera e microfone nas permissões do Android"
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        try {
            val bitmaps = uris.take(5).mapNotNull { uri ->
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }
            if (bitmaps.isNotEmpty()) {
                val storedPaths = bitmaps.mapNotNull { saveSceneImage(it) }
                if (storedPaths.size != bitmaps.size) {
                    activity.engineMessage = "Algumas imagens não puderam ser salvas no armazenamento do estúdio"
                }
                activity.engine?.setImages(bitmaps)
                imagePathsRaw = SceneDraftStore.encodePaths(storedPaths)
                imageCount = bitmaps.size
                imageLoaded = true
                val defaults = bitmaps.indices.map { index ->
                    when (index) {
                        0 -> ImageLayerPlacement(5f, 5f, 22f)
                        1 -> ImageLayerPlacement(70f, 5f, 22f)
                        2 -> ImageLayerPlacement(5f, 70f, 22f)
                        3 -> ImageLayerPlacement(70f, 70f, 22f)
                        else -> ImageLayerPlacement(38f, 38f, 22f)
                    }
                }
                imagePlacementsRaw = SceneDraftStore.encodePlacements(defaults)
                selectedImageLayer = 0
            } else if (uris.isNotEmpty()) {
                activity.engineMessage = "Nenhuma imagem pôde ser carregada"
            }
            if (uris.size > 5) activity.engineMessage = "Foram carregadas cinco imagens. Esse é o limite atual."
        } catch (error: Exception) {
            activity.engineMessage = "Erro ao carregar imagens"
        }
    }

    val mouthClosedPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    mouthClosedBitmap = bitmap
                    activity.engine?.setPngTuberImages(mouthClosedBitmap, mouthOpenBitmap)
                    activity.engineMessage = if (mouthOpenBitmap != null) "PNGTuber configurado" else "Imagem da boca fechada carregada; escolha a boca aberta"
                }
            } catch (error: Exception) {
                activity.engineMessage = "Não foi possível carregar a imagem da boca fechada"
            }
        }
    }

    val mouthOpenPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    mouthOpenBitmap = bitmap
                    activity.engine?.setPngTuberImages(mouthClosedBitmap, mouthOpenBitmap)
                    activity.engineMessage = if (mouthClosedBitmap != null) "PNGTuber configurado · boca reage ao microfone" else "Imagem da boca aberta carregada; escolha a boca fechada"
                }
            } catch (error: Exception) {
                activity.engineMessage = "Não foi possível carregar a imagem da boca aberta"
            }
        }
    }

    val chromaBackgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    activity.engine?.setChromaBackground(bitmap)
                    chromaBackgroundLoaded = true
                } else {
                    activity.engineMessage = "Não foi possível abrir o fundo escolhido"
                }
            } catch (error: Exception) {
                activity.engineMessage = "Erro ao carregar o fundo do chroma"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (permissionsOk) {
            activity.prepareEngine()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    fun applyComposition() {
        activity.engine?.setComposition(scene, caption, chroma, sensitivity, visualFilter, listOf(caption2, caption3))
        activity.engine?.setImagePlacements(imagePlacements)
    }

    LaunchedEffect(scene, caption, caption2, caption3, chroma, sensitivity, visualFilter, imagePlacementsRaw) {
        activity.engine?.setComposition(scene, caption, chroma, sensitivity, visualFilter, listOf(caption2, caption3))
        activity.engine?.setImagePlacements(imagePlacements)
    }

    LaunchedEffect(activity.engine, scene, imagePathsRaw) {
        val restored = imagePaths.mapNotNull { path -> BitmapFactory.decodeFile(path) }
        activity.engine?.setImages(restored)
        imageCount = restored.size
        imageLoaded = restored.isNotEmpty()
    }

    fun selectScene(target: String) {
        if (target == scene) return
        val existing = SceneDraftStore.decode(sceneDraftsRaw).toMutableMap()
        existing[scene] = SceneDraft(
            caption, caption2, caption3, chroma, visualFilter, sensitivity, imagePlacements, imagePaths
        )
        val targetDraft = existing[target] ?: SceneDraft()
        sceneDraftsRaw = SceneDraftStore.encode(existing)
        scene = target
        caption = targetDraft.caption
        caption2 = targetDraft.secondCaption
        caption3 = targetDraft.thirdCaption
        chroma = targetDraft.chroma
        visualFilter = targetDraft.visualFilter
        sensitivity = targetDraft.sensitivity
        imagePlacementsRaw = SceneDraftStore.encodePlacements(targetDraft.imagePlacements)
        imagePathsRaw = SceneDraftStore.encodePaths(targetDraft.imagePaths)
        selectedImageLayer = 0
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Color.Black,
            background = Bg,
            surface = Panel,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF111111), contentColor = Gold) {
                    StudioTab.values().forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {},
                            label = { Text(item.label, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Gold,
                                selectedTextColor = Gold,
                                indicatorColor = Color(0xFF382F1B),
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Header(activity.engine?.live == true, activity.engine?.recording == true)
                when (tab) {
                    StudioTab.LIVE -> LivePage(
                        activity = activity,
                        endpoint = endpoint,
                        onEndpointChange = { endpoint = it },
                        scene = scene,
                        onSceneChange = {
                            scene = it
                            applyComposition()
                        },
                        caption = caption,
                        onSwitchCamera = { activity.engine?.switchCamera() },
                        onStartLive = { activity.engine?.startLive(endpoint) },
                        onStopLive = { activity.engine?.stopLive() },
                        onStartRecord = { activity.engine?.startRecording() },
                        onStopRecord = { activity.engine?.stopRecording() },
                        permissionsOk = permissionsOk,
                        onRequestPermissions = {
                            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                        },
                        screenMode = screenMode,
                        onStartScreenCapture = {
                            val manager = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                        },
                        onStopScreenCapture = {
                            activity.engine?.stopScreenCapture()
                            screenMode = false
                        }
                    )
                    StudioTab.SCENES -> ScenesPage(
                        selected = scene,
                        availableScenes = listOf("Câmera", "Iniciando", "Intervalo", "Tela preta") + customScenes,
                        onAddScene = { name ->
                            val cleanName = name.trim().take(28)
                            if (cleanName.isNotBlank() && cleanName !in (listOf("Câmera", "Iniciando", "Intervalo", "Tela preta") + customScenes)) {
                                customScenesRaw = (customScenes + cleanName).joinToString("\n")
                            }
                        },
                        onSelect = { selectScene(it); applyComposition() },
                        caption = caption,
                        onCaptionChange = { caption = it; applyComposition() }
                    )
                    StudioTab.SOURCES -> SourcesPage(
                        caption = caption,
                        onCaptionChange = { caption = it },
                        onApplyText = { applyComposition() },
                        onPickImage = { imagePicker.launch("image/*") },
                        imageLoaded = imageLoaded,
                        imageCount = imageCount,
                        selectedImageLayer = selectedImageLayer,
                        imagePlacements = imagePlacements,
                        onSelectImageLayer = { selectedImageLayer = it },
                        onPlacementChange = { index, placement ->
                            val next = imagePlacements.toMutableList()
                            while (next.size <= index) next.add(ImageLayerPlacement())
                            next[index] = placement
                            imagePlacementsRaw = SceneDraftStore.encodePlacements(next)
                            applyComposition()
                        },
                        onClearImages = {
                            activity.engine?.clearImages()
                            imagePathsRaw = SceneDraftStore.encodePaths(emptyList())
                            imagePlacementsRaw = SceneDraftStore.encodePlacements(emptyList())
                            imageCount = 0
                            imageLoaded = false
                        },
                        qrContent = qrContent,
                        onQrContentChange = { qrContent = it },
                        onGenerateQr = {
                            try {
                                val qrBitmap = QrCodeGenerator.create(qrContent)
                                val storedPath = saveSceneImage(qrBitmap)
                                val added = activity.engine?.addImageOverlay(qrBitmap) == true
                                if (added) {
                                    if (storedPath != null) {
                                        imagePathsRaw = SceneDraftStore.encodePaths(imagePaths + storedPath)
                                    }
                                    val nextPlacements = imagePlacements.toMutableList()
                                    nextPlacements.add(ImageLayerPlacement(70f, 70f, 20f))
                                    imagePlacementsRaw = SceneDraftStore.encodePlacements(nextPlacements)
                                    imageCount = (imageCount + 1).coerceAtMost(5)
                                    imageLoaded = true
                                    activity.engine?.setImagePlacements(nextPlacements)
                                    activity.engineMessage = if (storedPath != null) "QR Code adicionado e salvo nesta cena" else "QR Code adicionado; não foi possível salvá-lo para a próxima abertura"
                                } else {
                                    qrBitmap.recycle()
                                    if (storedPath != null) java.io.File(storedPath).delete()
                                }
                            } catch (error: Exception) {
                                activity.engineMessage = "Não foi possível criar o QR Code: ${error.localizedMessage ?: "texto inválido"}"
                            }
                        },
                        secondCaption = caption2, thirdCaption = caption3,
                        onSecondCaptionChange = { caption2 = it },
                        onThirdCaptionChange = { caption3 = it },
                        onPickMouthClosed = { mouthClosedPicker.launch("image/*") },
                        onPickMouthOpen = { mouthOpenPicker.launch("image/*") },
                        mouthClosedLoaded = mouthClosedBitmap != null,
                        mouthOpenLoaded = mouthOpenBitmap != null,
                        onPickChromaBackground = { chromaBackgroundPicker.launch("image/*") },
                        chromaBackgroundLoaded = chromaBackgroundLoaded,
                        chroma = chroma,
                        onChromaChange = {
                            chroma = it
                            applyComposition()
                        },
                        visualFilter = visualFilter,
                        onVisualFilterChange = {
                            visualFilter = it
                            applyComposition()
                        },
                        sensitivity = sensitivity,
                        onSensitivityChange = {
                            sensitivity = it
                            applyComposition()
                        },
                        engineReady = activity.engine != null
                    )
                    StudioTab.AUDIO -> AudioPage(
                        gain = micGain,
                        onGainChange = {
                            micGain = it
                            activity.engine?.setMicrophoneGain(it)
                        },
                        muted = muted,
                        onMuteChange = {
                            muted = it
                            activity.engine?.setMicrophoneMuted(it)
                        },
                        screenMode = screenMode,
                        systemGain = systemGain,
                        onSystemGainChange = {
                            systemGain = it
                            activity.engine?.setSystemAudioGain(it)
                        }
                    )
                    StudioTab.SETTINGS -> SettingsPage(
                        endpoint = endpoint,
                        onEndpointChange = { endpoint = it },
                        bitrate = bitrate,
                        onBitrateChange = {
                            bitrate = it
                            activity.engine?.setVideoBitrate(it)
                        },
                        status = activity.engineMessage,
                        bitrateKbps = activity.engine?.sentBitrateKbps ?: 0
                    )
                }
                Text(activity.engineMessage, color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Header(live: Boolean, recording: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("NOVASTREAM", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text("STUDIO  |  MOBILE BROADCAST ENGINE", color = Muted, fontSize = 8.sp, letterSpacing = 1.2.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusLabel(if (live) "AO VIVO" else if (recording) "GRAVANDO" else "ESTÚDIO", live || recording)
            Text("v0.8.0", color = Muted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun StatusLabel(text: String, active: Boolean) {
    Surface(color = if (active) Color(0xFF3B1717) else Color(0xFF272217), shape = RoundedCornerShape(50)) {
        Text(text, color = if (active) Color(0xFFFF7777) else Gold, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun LivePage(
    activity: MainActivity,
    endpoint: String,
    onEndpointChange: (String) -> Unit,
    scene: String,
    onSceneChange: (String) -> Unit,
    caption: String,
    onSwitchCamera: () -> Unit,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    permissionsOk: Boolean,
    onRequestPermissions: () -> Unit,
    screenMode: Boolean,
    onStartScreenCapture: () -> Unit,
    onStopScreenCapture: () -> Unit
) {
    val engine = activity.engine
    StudioCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("MONITOR DE PROGRAMA", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Text("720 × 1280  •  30 FPS", color = Muted, fontSize = 9.sp)
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                .background(Color.Black, RoundedCornerShape(14.dp))
        ) {
            if (screenMode) {
                Column(Modifier.align(Alignment.Center).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CAPTURA DE TELA ATIVA", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Vá para o jogo ou aplicativo que deseja transmitir. O NovaStream continuará capturando a tela.",
                        color = Muted, fontSize = 11.sp)
                }
            } else if (engine != null) {
                AndroidView(
                    factory = { OpenGlView(it) },
                    update = { activity.attachPreview(it) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOVASTREAM", color = Gold, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Aguardando câmera e microfone", color = Muted, fontSize = 10.sp)
                }
            }
            StatusLabel(if (engine?.live == true) "● TRANSMITINDO" else "PRÉVIA", engine?.live == true)
            Text(caption, color = Color.White, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(5.dp)).padding(7.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onSwitchCamera, enabled = engine != null && !screenMode, modifier = Modifier.weight(1f)) {
                Text("TROCAR CÂMERA", fontSize = 10.sp)
            }
            OutlinedButton(onClick = { onSceneChange(if (scene == "Câmera") "Intervalo" else "Câmera") },
                enabled = engine != null, modifier = Modifier.weight(1f)) {
                Text(if (scene == "Câmera") "INTERVALO" else "VOLTAR À CÂMERA", fontSize = 10.sp)
            }
        }
    }

    StudioCard {
        Text("DESTINO DA TRANSMISSÃO", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL RTMP / RTMPS / SRT") },
            placeholder = { Text("rtmp://servidor/app/chave") },
            singleLine = true
        )
        Text("Use o endereço do servidor junto com a chave fornecida pela plataforma. O aplicativo não armazena a chave após ser fechado.",
            color = Muted, fontSize = 10.sp)
        Button(
            onClick = if (engine?.live == true) onStopLive else onStartLive,
            enabled = engine != null && (engine.live || (!engine.connecting && endpoint.isNotBlank())),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (engine?.live == true) Color(0xFF9F2828) else Gold
            )
        ) {
            Text(if (engine?.live == true) "ENCERRAR TRANSMISSÃO" else if (engine?.connecting == true) "CONECTANDO…" else "INICIAR TRANSMISSÃO",
                color = if (engine?.live == true) Color.White else Color.Black, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = if (screenMode) onStopScreenCapture else onStartScreenCapture,
            enabled = engine != null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (screenMode) Color(0xFF9F2828) else Gold
            )
        ) {
            Text(
                if (screenMode) "PARAR COMPARTILHAMENTO DE TELA" else "COMPARTILHAR TELA / JOGO",
                color = if (screenMode) Color.White else Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = if (engine?.recording == true) onStopRecord else onStartRecord,
                enabled = engine != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (engine?.recording == true) "PARAR GRAVAÇÃO" else "GRAVAR MP4", fontSize = 10.sp)
            }
            OutlinedButton(onClick = onRequestPermissions, enabled = !permissionsOk, modifier = Modifier.weight(1f)) {
                Text("PERMISSÕES", fontSize = 10.sp)
            }
        }
    }

    StudioCard {
        Text("MODOS DO ESTÚDIO", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ModeTile("CÂMERA", !screenMode, Modifier.weight(1f))
            ModeTile("TELA", screenMode, Modifier.weight(1f))
            ModeTile("VTUBER", false, Modifier.weight(1f))
        }
        Text("Câmera, captura de tela e transmissão RTMP estão disponíveis. VTuber/VRM ainda está em desenvolvimento.",
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ModeTile(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(color = if (active) Color(0xFF3A301B) else Color(0xFF202020),
        shape = RoundedCornerShape(9.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = if (active) Gold else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(if (active) "ATIVO" else "PLANEJADO", color = Muted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun ScenesPage(
    selected: String,
    availableScenes: List<String>,
    onAddScene: (String) -> Unit,
    onSelect: (String) -> Unit,
    caption: String,
    onCaptionChange: (String) -> Unit
) {
    var newSceneName by remember { mutableStateOf("") }
    StudioCard {
        Text("CENAS", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Cada cena controla o que é enviado ao preview e ao encoder.", color = Muted, fontSize = 10.sp)
        availableScenes.forEach { item ->
            Button(
                onClick = { onSelect(item) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected == item) Gold else Color(0xFF292929)
                )
            ) {
                Text(item, color = if (selected == item) Color.Black else Color.White)
            }
        }
        OutlinedTextField(value = caption, onValueChange = onCaptionChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("Título / legenda da cena") })
        OutlinedTextField(value = newSceneName, onValueChange = { newSceneName = it.take(28) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Nome de uma nova cena") }, singleLine = true)
        Button(onClick = { onAddScene(newSceneName.trim()); newSceneName = "" },
            enabled = newSceneName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("CRIAR CENA") }
    }
    StudioCard {
        Text("CENAS PERSONALIZADAS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Os nomes das cenas criadas ficam salvos neste aparelho. Cenas personalizadas usam a tela de espera; fontes individuais por cena e layouts arrastáveis ainda não estão implementados.",
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SourcesPage(
    caption: String,
    onCaptionChange: (String) -> Unit,
    secondCaption: String,
    thirdCaption: String,
    onSecondCaptionChange: (String) -> Unit,
    onThirdCaptionChange: (String) -> Unit,
    onApplyText: () -> Unit,
    onPickImage: () -> Unit,
    imageLoaded: Boolean,
    imageCount: Int,
    selectedImageLayer: Int,
    imagePlacements: List<ImageLayerPlacement>,
    onSelectImageLayer: (Int) -> Unit,
    onPlacementChange: (Int, ImageLayerPlacement) -> Unit,
    onClearImages: () -> Unit,
    qrContent: String,
    onQrContentChange: (String) -> Unit,
    onGenerateQr: () -> Unit,
    onPickMouthClosed: () -> Unit,
    onPickMouthOpen: () -> Unit,
    mouthClosedLoaded: Boolean,
    mouthOpenLoaded: Boolean,
    onPickChromaBackground: () -> Unit,
    chromaBackgroundLoaded: Boolean,
    chroma: Boolean,
    onChromaChange: (Boolean) -> Unit,
    visualFilter: String,
    onVisualFilterChange: (String) -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    engineReady: Boolean
) {
    val activePlacement = imagePlacements.getOrNull(selectedImageLayer) ?: ImageLayerPlacement()
    StudioCard {
        Text("FONTES E SOBREPOSIÇÕES", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Adicione elementos que entram na composição enviada ao encoder.", color = Muted, fontSize = 10.sp)
        OutlinedTextField(value = caption, onValueChange = onCaptionChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("Texto 1 · título") }, singleLine = true)
        OutlinedTextField(value = secondCaption, onValueChange = onSecondCaptionChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("Texto 2 · informação") }, singleLine = true)
        OutlinedTextField(value = thirdCaption, onValueChange = onThirdCaptionChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("Texto 3 · redes / chamada") }, singleLine = true)
        Button(onClick = onApplyText, enabled = engineReady, modifier = Modifier.fillMaxWidth()) {
            Text("APLICAR OS TRÊS TEXTOS")
        }
        OutlinedButton(onClick = onPickImage, enabled = engineReady, modifier = Modifier.fillMaxWidth()) {
            Text("ADICIONAR IMAGENS / LOGOS  ·  $imageCount/5")
        }
        if (imageCount > 0) OutlinedButton(onClick = onClearImages, enabled = engineReady, modifier = Modifier.fillMaxWidth()) {
            Text("REMOVER TODAS AS IMAGENS")
        }
        Text("Até cinco imagens podem entrar na composição ao mesmo tempo.", color = Muted, fontSize = 10.sp)
        if (imageCount > 0) {
            Divider(color = Color(0xFF363636))
            Text("POSIÇÃO E TAMANHO DA CAMADA", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                (0 until imageCount).forEach { index ->
                    FilterChip(
                        selected = selectedImageLayer == index,
                        onClick = { onSelectImageLayer(index) },
                        label = { Text("Imagem ${index + 1}", fontSize = 9.sp) }
                    )
                }
            }
            Text("Horizontal: ${activePlacement.x.toInt()}%", color = Muted, fontSize = 10.sp)
            Slider(
                value = activePlacement.x,
                onValueChange = { onPlacementChange(selectedImageLayer, activePlacement.copy(x = it)) },
                valueRange = 0f..100f,
                enabled = engineReady
            )
            Text("Vertical: ${activePlacement.y.toInt()}%", color = Muted, fontSize = 10.sp)
            Slider(
                value = activePlacement.y,
                onValueChange = { onPlacementChange(selectedImageLayer, activePlacement.copy(y = it)) },
                valueRange = 0f..100f,
                enabled = engineReady
            )
            Text("Tamanho: ${activePlacement.scale.toInt()}%", color = Muted, fontSize = 10.sp)
            Slider(
                value = activePlacement.scale,
                onValueChange = { onPlacementChange(selectedImageLayer, activePlacement.copy(scale = it)) },
                valueRange = 5f..60f,
                enabled = engineReady
            )
        }
    }
    StudioCard {
        Text("QR CODE", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Gere um QR Code para Pix, redes sociais, site ou link de divulgação. Ele entra como uma fonte de imagem na transmissão.",
            color = Muted, fontSize = 10.sp)
        OutlinedTextField(value = qrContent, onValueChange = onQrContentChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("URL ou texto do QR Code") }, singleLine = true)
        Button(onClick = onGenerateQr, enabled = engineReady && qrContent.isNotBlank() && imageCount < 5,
            modifier = Modifier.fillMaxWidth()) { Text("GERAR E ADICIONAR QR CODE") }
    }
    StudioCard {
        Text("AVATAR 2D · PNGTUBER", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Escolha duas imagens PNG transparentes do mesmo personagem. A boca alterna quando o microfone detecta voz.",
            color = Muted, fontSize = 10.sp)
        OutlinedButton(onClick = onPickMouthClosed, enabled = engineReady, modifier = Modifier.fillMaxWidth()) {
            Text(if (mouthClosedLoaded) "TROCAR BOCA FECHADA" else "ESCOLHER PNG · BOCA FECHADA")
        }
        OutlinedButton(onClick = onPickMouthOpen, enabled = engineReady, modifier = Modifier.fillMaxWidth()) {
            Text(if (mouthOpenLoaded) "TROCAR BOCA ABERTA" else "ESCOLHER PNG · BOCA ABERTA")
        }
        Text(
            when {
                mouthClosedLoaded && mouthOpenLoaded -> "PNGTuber pronto · fala detectada pelo microfone"
                mouthClosedLoaded || mouthOpenLoaded -> "Falta selecionar a segunda imagem"
                else -> "Nenhum avatar selecionado"
            },
            color = Muted,
            fontSize = 10.sp
        )
        Text("Este modo é PNGTuber 2D. Rastreamento facial e modelos 3D VRM ainda não estão disponíveis.",
            color = Muted, fontSize = 10.sp)
    }
    StudioCard {
        Text("EFEITOS DE CÂMERA", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Filtros processados na GPU e enviados junto com o vídeo.", color = Muted, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Normal", "Beleza", "Sépia", "P&B", "Vibrante").forEach { item ->
                FilterChip(
                    selected = visualFilter == item,
                    onClick = { onVisualFilterChange(item) },
                    label = { Text(item, fontSize = 10.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    StudioCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CHROMA KEY", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Remove o verde da câmera e coloca uma imagem atrás da pessoa usando a GPU", color = Muted, fontSize = 10.sp)
            }
            Switch(checked = chroma, onCheckedChange = onChromaChange, enabled = engineReady)
        }
        OutlinedButton(
            onClick = onPickChromaBackground,
            enabled = engineReady,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (chromaBackgroundLoaded) "TROCAR FUNDO DO CHROMA" else "ESCOLHER IMAGEM DE FUNDO")
        }
        Text(if (chromaBackgroundLoaded) "Fundo personalizado carregado" else "Sem fundo escolhido: será usado fundo escuro",
            color = Muted, fontSize = 10.sp)
        Text("Sensibilidade", color = Color.White, fontSize = 11.sp)
        Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 0.1f..1f, enabled = chroma && engineReady)
        Text("Use tecido verde uniforme, iluminação forte e evite roupas verdes. O recorte depende da iluminação e da câmera.",
            color = Muted, fontSize = 10.sp)
    }
    StudioCard {
        Text("PRÓXIMAS FONTES", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Vídeo e playlist, páginas web, alertas, QR Code e chat unificado ainda precisam dos módulos próprios. Eles não estão ativos nesta versão.",
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun AudioPage(
    gain: Float,
    onGainChange: (Float) -> Unit,
    muted: Boolean,
    onMuteChange: (Boolean) -> Unit,
    screenMode: Boolean,
    systemGain: Float,
    onSystemGainChange: (Float) -> Unit
) {
    StudioCard {
        Text("MIXER DE ÁUDIO", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Microfone", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Entrada principal", color = Muted, fontSize = 10.sp)
            }
            Switch(checked = !muted, onCheckedChange = { onMuteChange(!it) })
        }
        Text(if (muted) "Microfone silenciado" else "Ganho: ${(gain * 100).toInt()}%", color = Muted, fontSize = 10.sp)
        Slider(value = gain, onValueChange = onGainChange, valueRange = 0f..2f)
        if (screenMode) {
            Divider(color = Color(0xFF363636))
            Text("ÁUDIO INTERNO DO JOGO / APLICATIVO", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Ganho: ${(systemGain * 100).toInt()}%", color = Muted, fontSize = 10.sp)
            Slider(value = systemGain, onValueChange = onSystemGainChange, valueRange = 0f..2f)
        }
    }
    StudioCard {
        Text("PROCESSAMENTO", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Cancelamento de eco e supressão de ruído são solicitados ao Android na preparação da entrada de microfone.",
            color = Muted, fontSize = 10.sp)
        Divider(color = Color(0xFF363636))
        Text(if (screenMode) "O modo de captura mistura o microfone e o áudio interno autorizado pelo Android. Aplicativos protegidos podem impedir a captura do próprio áudio." else "Ao ativar a captura de tela, o áudio interno do jogo será disponibilizado neste mixer. Canais separados de música e alertas exigem fontes adicionais.",
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsPage(
    endpoint: String,
    onEndpointChange: (String) -> Unit,
    bitrate: Float,
    onBitrateChange: (Float) -> Unit,
    status: String,
    bitrateKbps: Long
) {
    StudioCard {
        Text("SAÍDA E QUALIDADE", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL do servidor + chave") },
            placeholder = { Text("rtmp://...") },
            singleLine = true
        )
        Text("Perfil atual: H.264 · 720 × 1280 · 30 FPS · AAC 44,1 kHz", color = Muted, fontSize = 10.sp)
        Text("Bitrate de vídeo: ${"%.1f".format(bitrate)} Mbps", color = Color.White, fontSize = 11.sp)
        Slider(value = bitrate, onValueChange = onBitrateChange, valueRange = 0.8f..6f)
        Text("Durante uma live o bitrate é ajustado no encoder. Antes de iniciar, a configuração padrão permanece em 3,5 Mbps.",
            color = Muted, fontSize = 10.sp)
    }
    StudioCard {
        Text("MONITOR DE REDE", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Bitrate reportado: ${bitrateKbps} kbps", color = Color.White, fontSize = 12.sp)
        Text(status, color = Muted, fontSize = 10.sp)
    }
    StudioCard {
        Text("DESTINOS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Esta versão aceita um destino RTMP/SRT por vez. Multistream exige relay próprio ou integração oficial com cada plataforma; não é ativado por simplesmente inserir várias chaves.",
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun StudioCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
