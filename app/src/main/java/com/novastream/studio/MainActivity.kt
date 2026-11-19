package com.novastream.studio

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Bundle
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
    var tab by remember { mutableStateOf(StudioTab.LIVE) }
    var permissionsOk by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var endpoint by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("YLTON ROCHA  |  AO VIVO") }
    var scene by remember { mutableStateOf("Câmera") }
    var chroma by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(0.72f) }
    var micGain by remember { mutableFloatStateOf(1f) }
    var bitrate by remember { mutableFloatStateOf(3.5f) }
    var muted by remember { mutableStateOf(false) }
    var imageLoaded by remember { mutableStateOf(false) }
    var chromaBackgroundLoaded by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsOk = result[Manifest.permission.CAMERA] == true &&
            result[Manifest.permission.RECORD_AUDIO] == true
        if (permissionsOk) activity.prepareEngine()
        else activity.engineMessage = "Libere câmera e microfone nas permissões do Android"
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap != null) {
                    activity.engine?.setImage(bitmap)
                    imageLoaded = true
                } else {
                    activity.engineMessage = "Não foi possível abrir essa imagem"
                }
            } catch (error: Exception) {
                activity.engineMessage = "Erro ao carregar imagem"
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
        activity.engine?.setComposition(scene, caption, chroma, sensitivity)
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
                        }
                    )
                    StudioTab.SCENES -> ScenesPage(
                        selected = scene,
                        onSelect = {
                            scene = it
                            applyComposition()
                        },
                        caption = caption,
                        onCaptionChange = {
                            caption = it
                            applyComposition()
                        }
                    )
                    StudioTab.SOURCES -> SourcesPage(
                        caption = caption,
                        onCaptionChange = { caption = it },
                        onApplyText = { applyComposition() },
                        onPickImage = { imagePicker.launch("image/*") },
                        imageLoaded = imageLoaded,
                        onPickChromaBackground = { chromaBackgroundPicker.launch("image/*") },
                        chromaBackgroundLoaded = chromaBackgroundLoaded,
                        chroma = chroma,
                        onChromaChange = {
                            chroma = it
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
            Text("v0.5.0", color = Muted, fontSize = 9.sp)
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
    onRequestPermissions: () -> Unit
) {
    val engine = activity.engine
    StudioCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("MONITOR DE PROGRAMA", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Text("720 × 1280  •  30 FPS", color = Muted, fontSize = 9.sp)
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(9f / 12.5f)
                .background(Color.Black, RoundedCornerShape(14.dp))
        ) {
            if (engine != null) {
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
            OutlinedButton(onClick = onSwitchCamera, enabled = engine != null, modifier = Modifier.weight(1f)) {
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
            ModeTile("CÂMERA", true, Modifier.weight(1f))
            ModeTile("TELA", false, Modifier.weight(1f))
            ModeTile("VTUBER", false, Modifier.weight(1f))
        }
        Text("Câmera e transmissão RTMP estão ativadas nesta versão. Captura de tela e VTuber estão planejados para módulos separados.",
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
    onSelect: (String) -> Unit,
    caption: String,
    onCaptionChange: (String) -> Unit
) {
    StudioCard {
        Text("CENAS", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Cada cena controla o que é enviado ao preview e ao encoder.", color = Muted, fontSize = 10.sp)
        listOf("Câmera", "Iniciando", "Intervalo", "Tela preta").forEach { item ->
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
        OutlinedTextField(
            value = caption,
            onValueChange = onCaptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Título / legenda da cena") }
        )
    }
    StudioCard {
        Text("ORGANIZAÇÃO DE CENAS", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("A estrutura aceita presets independentes. A próxima etapa adicionará salvar, duplicar e reordenar cenas sem alterar o motor de transmissão.",
            color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SourcesPage(
    caption: String,
    onCaptionChange: (String) -> Unit,
    onApplyText: () -> Unit,
    onPickImage: () -> Unit,
    imageLoaded: Boolean,
    onPickChromaBackground: () -> Unit,
    chromaBackgroundLoaded: Boolean,
    chroma: Boolean,
    onChromaChange: (Boolean) -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    engineReady: Boolean
) {
    StudioCard {
        Text("FONTES E SOBREPOSIÇÕES", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Adicione elementos que entram na composição enviada ao encoder.", color = Muted, fontSize = 10.sp)
        OutlinedTextField(
            value = caption,
            onValueChange = onCaptionChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Texto da transmissão") }
        )
        Button(onClick = onApplyText, enabled = engineReady, modifier = Modifier.fillMaxWidth()) {
            Text("APLICAR TEXTO NA TRANSMISSÃO")
        }
        OutlinedButton(onClick = onPickImage, enabled = engineReady, modifier = Modifier.fillMaxWidth()) {
            Text(if (imageLoaded) "TROCAR IMAGEM / LOGO" else "ADICIONAR IMAGEM / LOGO")
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
    onMuteChange: (Boolean) -> Unit
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
    }
    StudioCard {
        Text("PROCESSAMENTO", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Cancelamento de eco e supressão de ruído são solicitados ao Android na preparação da entrada de microfone.",
            color = Muted, fontSize = 10.sp)
        Divider(color = Color(0xFF363636))
        Text("Áudio interno do jogo e canais separados (música / alertas) serão adicionados com captura de tela e mixer PCM dedicado.",
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
