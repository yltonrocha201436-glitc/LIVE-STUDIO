package com.novastream.studio

import com.pedro.encoder.input.audio.CustomAudioEffect
import kotlin.math.sqrt

/**
 * Measures microphone PCM level and emits voice-active transitions.
 * Audio bytes are passed through unchanged to the encoder.
 */
class PngTuberAudioEffect(
    private val threshold: Float = 950f,
    private val onVoiceActiveChanged: (Boolean) -> Unit
) : CustomAudioEffect() {

    @Volatile
    private var voiceActive = false

    override fun process(pcmBuffer: ByteArray): ByteArray {
        val samples = pcmBuffer.size / 2
        if (samples <= 0) return pcmBuffer

        var sumSquares = 0.0
        var index = 0
        while (index + 1 < pcmBuffer.size) {
            val low = pcmBuffer[index].toInt() and 0xFF
            val high = pcmBuffer[index + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            sumSquares += sample.toDouble() * sample.toDouble()
            index += 2
        }

        val rms = sqrt(sumSquares / samples).toFloat()
        val nextState = if (voiceActive) rms >= threshold * 0.62f else rms >= threshold

        if (nextState != voiceActive) {
            voiceActive = nextState
            onVoiceActiveChanged(nextState)
        }
        return pcmBuffer
    }
}
