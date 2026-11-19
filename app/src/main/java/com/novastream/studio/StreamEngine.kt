package com.novastream.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.media.projection.MediaProjection
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.BlackFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.gl.render.filters.`object`.TextObjectFilterRender
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.encoder.input.sources.audio.MixAudioSource
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.library.base.recording.RecordController
import com.pedro.library.generic.GenericStream
import com.pedro.library.view.OpenGlView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Motor de mídia isolado da interface.
 *
 * Um único pipeline OpenGL compõe a prévia e o vídeo codificado, evitando
 * manter um caminho separado para cada sobreposição.
 */
class StreamEngine(
    context: Context,
    private val onUiUpdate: (String) -> Unit
) : ConnectChecker {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stream = GenericStream(appContext, this)

    var prepared: Boolean = false
        private set
    var live: Boolean = false
        private set
    var connecting: Boolean = false
        private set
    var recording: Boolean = false
        private set
    var screenCaptureActive: Boolean = false
        private set
    var muted: Boolean = false
        private set
    var status: String = "Preparando motor de vídeo"
        private set
    var sentBitrateKbps: Long = 0
        private set

    private var currentScene = "Câmera"
    private var currentCaption = ""
    private var currentImage: Bitmap? = null
    private var chromaBackground: Bitmap? = null
    private var chromaEnabled = false
    private var chromaSensitivity = 0.72f
    private var micGain = 1.0f
    private var systemAudioGain = 1.0f

    fun prepare(): Boolean {
        return try {
            val videoReady = stream.prepareVideo(
                720, 1280, 3_500_000, 30, 2, 0
            )
            val audioReady = stream.prepareAudio(
                44_100, true, 128_000, true, true
            )
            prepared = videoReady && audioReady
            publish(
                if (prepared) "Estúdio pronto · 720 × 1280 · 30 FPS"
                else "O aparelho não aceitou a configuração de áudio/vídeo"
            )
            prepared
        } catch (error: Exception) {
            prepared = false
            publish("Falha ao preparar o motor: ${error.localizedMessage ?: "erro desconhecido"}")
            false
        }
    }

    fun attachPreview(view: OpenGlView) {
        if (!prepared) return
        try {
            if (!stream.isOnPreview) {
                stream.startPreview(view, true)
            }
        } catch (error: Exception) {
            publish("Prévia: ${error.localizedMessage ?: "não foi possível iniciar"}")
        }
    }

    fun switchCamera() {
        try {
            val source = stream.videoSource as? Camera2Source
            if (source == null) {
                publish("A fonte atual não é a câmera")
                return
            }
            source.switchCamera()
            publish("Câmera alternada")
        } catch (error: Exception) {
            publish("Não foi possível trocar a câmera: ${error.localizedMessage ?: "erro"}")
        }
    }


    /**
     * Switches the video source from the camera to the Android display.
     * Android 10+ also mixes app playback audio with the microphone.
     */
    fun startScreenCapture(projection: MediaProjection) {
        if (!prepared) {
            publish("Prepare o estúdio antes de compartilhar a tela")
            return
        }
        if (screenCaptureActive) return
        try {
            stream.changeVideoSource(ScreenSource(appContext, projection))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                stream.changeAudioSource(MixAudioSource(projection))
                setMicrophoneGain(micGain)
                setSystemAudioGain(systemAudioGain)
                if (muted) setMicrophoneMuted(true)
            }
            screenCaptureActive = true
            publish("Captura de tela ativa · abra o jogo ou aplicativo que deseja transmitir")
        } catch (error: Exception) {
            screenCaptureActive = false
            try { stream.changeVideoSource(Camera2Source(appContext)) } catch (_: Exception) {}
            try { stream.changeAudioSource(MicrophoneSource()) } catch (_: Exception) {}
            publish("Não foi possível iniciar a captura: ${error.localizedMessage ?: "erro de projeção"}")
            ScreenProjectionService.stop(appContext)
            if (live) StudioForegroundService.start(appContext)
        }
    }

    fun onScreenCaptureEndedBySystem() {
        if (!screenCaptureActive) return
        try {
            stream.changeVideoSource(Camera2Source(appContext))
            stream.changeAudioSource(MicrophoneSource())
            setMicrophoneGain(micGain)
            if (muted) setMicrophoneMuted(true)
            screenCaptureActive = false
            if (live) StudioForegroundService.start(appContext)
            publish("A captura de tela foi encerrada pelo Android · câmera restaurada")
        } catch (error: Exception) {
            screenCaptureActive = false
            if (live) StudioForegroundService.start(appContext)
            publish("A captura foi encerrada: ${error.localizedMessage ?: "fonte indisponível"}")
        }
    }

    fun stopScreenCapture() {
        if (!screenCaptureActive) {
            ScreenProjectionService.stop(appContext)
            return
        }
        try {
            stream.changeVideoSource(Camera2Source(appContext))
            stream.changeAudioSource(MicrophoneSource())
            setMicrophoneGain(micGain)
            if (muted) setMicrophoneMuted(true)
            screenCaptureActive = false
            publish("Captura encerrada · câmera restaurada")
        } catch (error: Exception) {
            publish("Falha ao voltar para a câmera: ${error.localizedMessage ?: "erro"}")
        } finally {
            ScreenProjectionService.stop(appContext)
        }
    }

    fun startLive(endpoint: String) {
        val target = endpoint.trim()
        if (!prepared) {
            publish("Aguarde a preparação do estúdio")
            return
        }
        if (!target.startsWith("rtmp://", true) && !target.startsWith("rtmps://", true)
            && !target.startsWith("srt://", true)) {
            publish("Informe um endereço RTMP, RTMPS ou SRT válido")
            return
        }
        if (live) return
        try {
            connecting = true
            publish("Conectando ao destino…")
            if (!screenCaptureActive) StudioForegroundService.start(appContext)
            stream.startStream(target)
        } catch (error: Exception) {
            connecting = false
            StudioForegroundService.stop(appContext)
            publish("Falha ao iniciar transmissão: ${error.localizedMessage ?: "erro"}")
        }
    }

    fun stopLive() {
        if (!live && !connecting && !stream.isStreaming) return
        try {
            stream.stopStream()
            live = false
            connecting = false
            StudioForegroundService.stop(appContext)
            publish("Transmissão encerrada")
        } catch (error: Exception) {
            StudioForegroundService.stop(appContext)
            publish("Erro ao encerrar live: ${error.localizedMessage ?: "erro"}")
        }
    }

    fun startRecording(): Boolean {
        if (!prepared || recording) return false
        return try {
            val folder = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: appContext.filesDir
            val novaFolder = File(folder, "NovaStreamStudio")
            if (!novaFolder.exists() && !novaFolder.mkdirs()) {
                throw IllegalStateException("Não foi possível criar a pasta de gravações")
            }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(novaFolder, "NovaStream_$stamp.mp4")
            stream.startRecord(file.absolutePath, listener = object : RecordController.Listener {
                override fun onStatusChange(status: RecordController.Status) {
                    when (status) {
                        RecordController.Status.STARTED,
                        RecordController.Status.RECORDING,
                        RecordController.Status.RESUMED -> {
                            recording = true
                            publish("Gravando: ${file.name}")
                        }
                        RecordController.Status.STOPPED -> {
                            recording = false
                            publish("Gravação salva: ${file.absolutePath}")
                        }
                        RecordController.Status.PAUSED -> {
                            publish("Gravação pausada")
                        }
                    }
                }
            })
            recording = true
            publish("Gravação iniciada")
            true
        } catch (error: Exception) {
            recording = false
            publish("Não foi possível gravar: ${error.localizedMessage ?: "erro"}")
            false
        }
    }

    fun stopRecording() {
        if (!recording && !stream.isRecording) return
        try {
            stream.stopRecord()
            recording = false
            publish("Gravação finalizada")
        } catch (error: Exception) {
            publish("Erro ao finalizar gravação: ${error.localizedMessage ?: "erro"}")
        }
    }

    fun setMicrophoneMuted(value: Boolean) {
        muted = value
        when (val source = stream.audioSource) {
            is MicrophoneSource -> {
                if (value) source.mute() else source.unMute()
            }
            is MixAudioSource -> {
                source.microphoneVolume = if (value) 0f else micGain
            }
        }
        publish(if (value) "Microfone silenciado · áudio interno preservado" else "Microfone ativo")
    }

    fun setMicrophoneGain(value: Float) {
        micGain = value.coerceIn(0f, 2f)
        when (val source = stream.audioSource) {
            is MicrophoneSource -> source.microphoneVolume = if (muted) 0f else micGain
            is MixAudioSource -> source.microphoneVolume = if (muted) 0f else micGain
        }
    }

    fun setSystemAudioGain(value: Float) {
        systemAudioGain = value.coerceIn(0f, 2f)
        (stream.audioSource as? MixAudioSource)?.internalVolume = systemAudioGain
    }

    fun setVideoBitrate(valueMbps: Float) {
        val bitsPerSecond = (valueMbps.coerceIn(0.8f, 6.0f) * 1_000_000f).toInt()
        if (prepared && stream.isStreaming) {
            stream.setVideoBitrateOnFly(bitsPerSecond)
        }
    }

    fun setImage(bitmap: Bitmap?) {
        currentImage = bitmap
        refreshComposition()
        publish(if (bitmap == null) "Imagem de sobreposição removida" else "Imagem adicionada à composição")
    }

    fun setChromaBackground(bitmap: Bitmap?) {
        chromaBackground = bitmap
        refreshComposition()
        publish(if (bitmap == null) "Fundo do chroma removido" else "Fundo do chroma atualizado")
    }

    fun setComposition(
        scene: String,
        caption: String,
        chroma: Boolean,
        sensitivity: Float
    ) {
        currentScene = scene
        currentCaption = caption
        chromaEnabled = chroma
        chromaSensitivity = sensitivity.coerceIn(0.1f, 1f)
        refreshComposition()
    }

    private fun refreshComposition() {
        if (!prepared) return
        try {
            val gl = stream.getGlInterface()
            gl.clearFilters()

            // Cenas de espera usam fundo preto; as fontes são desenhadas acima dele.
            if (currentScene != "Câmera") {
                gl.addFilter(BlackFilterRender())
            }

            // GPU chroma key: remove green pixels from the camera and composes
            // the selected background beneath the retained foreground.
            if (currentScene == "Câmera" && chromaEnabled) {
                val background = chromaBackground ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(AndroidColor.rgb(18, 18, 22))
                    chromaBackground = it
                }
                gl.addFilter(ChromaKeyReplaceFilter(background, chromaSensitivity))
            }

            currentImage?.let { bitmap ->
                val imageFilter = ImageObjectFilterRender().apply {
                    setImage(bitmap)
                    setScale(26f, 26f)
                    setPosition(5f, 5f)
                    setAlpha(1f)
                }
                gl.addFilter(imageFilter)
            }

            if (currentCaption.isNotBlank()) {
                val textFilter = TextObjectFilterRender().apply {
                    setText(currentCaption, 34f, AndroidColor.WHITE, AndroidColor.TRANSPARENT)
                    setScale(86f, 14f)
                    setPosition(7f, 82f)
                    setAlpha(1f)
                }
                gl.addFilter(textFilter)
            }
            publish("Composição atualizada · $currentScene")
        } catch (error: Exception) {
            publish("Erro na composição: ${error.localizedMessage ?: "filtro"}")
        }
    }

    fun release() {
        try {
            if (stream.isRecording) stream.stopRecord()
            if (stream.isStreaming) stream.stopStream()
            if (screenCaptureActive) stopScreenCapture()
            if (stream.isOnPreview) stream.stopPreview()
            stream.release()
        } catch (_: Exception) {
            if (screenCaptureActive) ScreenProjectionService.stop(appContext)
            StudioForegroundService.stop(appContext)
        }
        currentImage?.recycle()
        currentImage = null
        chromaBackground?.recycle()
        chromaBackground = null
        prepared = false
        live = false
        recording = false
    }

    private fun publish(message: String) {
        mainHandler.post {
            status = message
            onUiUpdate(message)
        }
    }

    override fun onConnectionStarted(url: String) {
        publish("Conectando ao servidor RTMP/SRT…")
    }

    override fun onConnectionSuccess() {
        mainHandler.post {
            connecting = false
            live = true
            status = "Transmissão ao vivo"
            onUiUpdate(status)
        }
    }

    override fun onConnectionFailed(reason: String) {
        mainHandler.post {
            connecting = false
            live = false
            StudioForegroundService.stop(appContext)
            status = "Falha na transmissão: $reason"
            onUiUpdate(status)
        }
    }

    override fun onDisconnect() {
        mainHandler.post {
            connecting = false
            live = false
            StudioForegroundService.stop(appContext)
            status = "Desconectado do servidor"
            onUiUpdate(status)
        }
    }

    override fun onAuthError() {
        mainHandler.post {
            connecting = false
            live = false
            StudioForegroundService.stop(appContext)
            status = "Falha de autenticação. Confira a chave de transmissão."
            onUiUpdate(status)
        }
    }

    override fun onAuthSuccess() {
        publish("Autenticação aceita")
    }

    override fun onNewBitrate(bitrate: Long) {
        mainHandler.post {
            sentBitrateKbps = bitrate / 1000
            onUiUpdate(status)
        }
    }
}
