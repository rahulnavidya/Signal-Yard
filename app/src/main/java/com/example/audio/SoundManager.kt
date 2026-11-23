package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Offline synthesized tactile railway sound engine.
 * Generates clean PCM tones/mechanical clicks directly in memory with zero external network or audio file dependencies.
 */
class SoundManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var volume: Float = 0.75f

    private val sampleRate = 22050

    private val switchClackPcm: ShortArray by lazy { synthesizeSwitchClack() }
    private val signalBellPcm: ShortArray by lazy { synthesizeSignalBell() }
    private val arrivalChimePcm: ShortArray by lazy { synthesizeArrivalChime() }
    private val trainHornPcm: ShortArray by lazy { synthesizeTrainHorn() }
    private val rejectPulsePcm: ShortArray by lazy { synthesizeRejectPulse() }

    fun playSwitchToggle() = playBuffer(switchClackPcm, 0.75f)
    fun playSignalRelay() = playBuffer(signalBellPcm, 0.55f)
    fun playStationBell() = playBuffer(arrivalChimePcm, 0.65f)
    fun playExpressHorn() = playBuffer(trainHornPcm, 0.50f)
    fun playActionRejected() = playBuffer(rejectPulsePcm, 0.60f)

    private fun playBuffer(pcm: ShortArray, gain: Float) {
        if (!enabled || volume <= 0.01f) return
        val effectiveVol = (volume * gain).coerceIn(0f, 1f)
        scope.launch {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.setVolume(effectiveVol)
                track.play()
                val durationMs = (pcm.size * 1000L) / sampleRate + 60L
                kotlinx.coroutines.delay(durationMs)
                track.release()
            }
        }
    }

    private fun synthesizeSwitchClack(): ShortArray {
        val count = (sampleRate * 0.09f).toInt()
        return ShortArray(count) { i ->
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 48f)
            val click1 = sin(2.0 * PI * 320.0 * t)
            val click2 = sin(2.0 * PI * 890.0 * t) * 0.5
            val thud = sin(2.0 * PI * 145.0 * t) * 0.7
            ((click1 + click2 + thud) * env * 12000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun synthesizeSignalBell(): ShortArray {
        val count = (sampleRate * 0.16f).toInt()
        return ShortArray(count) { i ->
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 18f)
            val bell = sin(2.0 * PI * 1046.5 * t) + 0.4 * sin(2.0 * PI * 1568.0 * t)
            (bell * env * 9000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun synthesizeArrivalChime(): ShortArray {
        val count = (sampleRate * 0.36f).toInt()
        return ShortArray(count) { i ->
            val t = i.toFloat() / sampleRate
            val note1 = if (t < 0.16f) sin(2.0 * PI * 659.25 * t) * exp(-t * 14f) else 0.0
            val t2 = (t - 0.12f).coerceAtLeast(0f)
            val note2 = if (t >= 0.12f) sin(2.0 * PI * 880.0 * t2) * exp(-t2 * 12f) else 0.0
            ((note1 + note2) * 11000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun synthesizeTrainHorn(): ShortArray {
        val count = (sampleRate * 0.34f).toInt()
        return ShortArray(count) { i ->
            val t = i.toFloat() / sampleRate
            val attack = (t / 0.03f).coerceAtMost(1f)
            val release = ((0.34f - t) / 0.08f).coerceIn(0f, 1f)
            val env = attack * release
            val chord = sin(2.0 * PI * 277.18 * t) + 0.85 * sin(2.0 * PI * 349.23 * t)
            (chord * env * 8500.0).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun synthesizeRejectPulse(): ShortArray {
        val count = (sampleRate * 0.14f).toInt()
        return ShortArray(count) { i ->
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 20f)
            val buzz = sin(2.0 * PI * 190.0 * t) + 0.5 * sin(2.0 * PI * 205.0 * t)
            (buzz * env * 10000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
}
