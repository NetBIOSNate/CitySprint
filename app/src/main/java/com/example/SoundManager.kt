package com.example

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundManager {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun init() {
    }

    private fun playTone(frequency: Double, durationMs: Int) {
        scope.launch {
            try {
                val sampleRate = 44100
                val numSamples = (durationMs * sampleRate) / 1000
                val generatedSnd = ByteArray(2 * numSamples)
                
                for (i in 0 until numSamples) {
                    val dVal = sin(2.0 * Math.PI * i.toDouble() / (sampleRate / frequency))
                    val valShort = (dVal * 32767).toInt().toShort()
                    generatedSnd[i * 2] = (valShort.toInt() and 0x00ff).toByte()
                    generatedSnd[i * 2 + 1] = ((valShort.toInt() and 0xff00) ushr 8).toByte()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_GAME)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(generatedSnd.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(generatedSnd, 0, generatedSnd.size)
                audioTrack.play()
                
                kotlinx.coroutines.delay(durationMs.toLong() + 100)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playJumpSound() {
        playTone(440.0, 100) // A4
    }

    fun playCollectSound() {
        playTone(880.0, 150) // A5
    }

    fun playGameOverSound() {
        playTone(220.0, 500) // A3
    }

    fun release() {
    }
}
