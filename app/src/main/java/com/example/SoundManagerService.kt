package com.example

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class AmbientNoiseType(val label: String) {
    TRAFFIC("TRAFFIC"),
    SIREN("SIREN"),
    CROWD("CROWD")
}

data class CityAcousticProfile(
    val city: City,
    val surfaceName: String,
    val ambientSummary: String,
    val trafficDroneFreq: Float,
    val trafficVolume: Float,
    val crowdDensity: Float,
    val crowdFormantHz: Float,
    val hornBaseFreq: Float,
    val sirenCycleSeconds: Float
)

/**
 * Bound and started Android Service that synthesizes and mixes real-time
 * city-specific ambient soundscapes (traffic, sirens, crowds) and surface-accurate
 * character footstep and movement sounds with zero external asset dependencies.
 */
class SoundManagerService : Service() {

    inner class SoundServiceBinder : Binder() {
        fun getService(): SoundManagerService = this@SoundManagerService
    }

    private val binder = SoundServiceBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var audioLoopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        SoundManager.attachService(this)
        startAudioEngineIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SoundManager.attachService(this)
        startAudioEngineIfNeeded()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        audioLoopJob?.cancel()
        SoundManager.detachService(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    fun startAudioEngineIfNeeded() {
        if (audioLoopJob?.isActive == true) return
        audioLoopJob = serviceScope.launch {
            SoundManager.runStreamingAudioLoop { isActive }
        }
    }

    fun triggerFootstep(city: City, isLeftFoot: Boolean, isSprinting: Boolean) {
        SoundManager.enqueueFootstepVoice(city, isLeftFoot, isSprinting)
    }

    fun triggerAmbientNoise(city: City, type: AmbientNoiseType) {
        SoundManager.enqueueAmbientBurstVoice(city, type)
    }
}
