package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object SoundManager {
    private const val SAMPLE_RATE = 22050
    private const val CHUNK_SAMPLES = 441 // 20ms per mixing frame at 22050 Hz

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var standaloneLoopJob: Job? = null
    private var boundService: SoundManagerService? = null
    private var appContext: Context? = null
    private var isServiceBound = false

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _currentCity = MutableStateFlow(City.NEW_YORK)
    val currentCity: StateFlow<City> = _currentCity.asStateFlow()

    private val _isAmbientActive = MutableStateFlow(false)
    val isAmbientActive: StateFlow<Boolean> = _isAmbientActive.asStateFlow()

    private val _isSprinting = MutableStateFlow(false)

    private val _activeSoundStatus = MutableStateFlow("NYC: ASPHALT • TRAFFIC & SIRENS")
    val activeSoundStatus: StateFlow<String> = _activeSoundStatus.asStateFlow()

    private val _footstepCount = MutableStateFlow(0)
    val footstepCount: StateFlow<Int> = _footstepCount.asStateFlow()

    private val _lastTriggeredAmbient = MutableStateFlow<AmbientNoiseType?>(null)
    val lastTriggeredAmbient: StateFlow<AmbientNoiseType?> = _lastTriggeredAmbient.asStateFlow()

    private data class ActiveVoice(
        val samples: ShortArray,
        var position: Int = 0,
        val gain: Float = 1.0f
    )

    private val activeVoices = ConcurrentLinkedQueue<ActiveVoice>()

    private val cityProfiles: Map<City, CityAcousticProfile> = mapOf(
        City.NEW_YORK to CityAcousticProfile(
            city = City.NEW_YORK,
            surfaceName = "URBAN ASPHALT",
            ambientSummary = "MIDTOWN TRAFFIC • NYPD SIREN • CROWD",
            trafficDroneFreq = 68f,
            trafficVolume = 0.24f,
            crowdDensity = 0.20f,
            crowdFormantHz = 540f,
            hornBaseFreq = 415f,
            sirenCycleSeconds = 6.5f
        ),
        City.PARIS to CityAcousticProfile(
            city = City.PARIS,
            surfaceName = "COBBLESTONE PAVERS",
            ambientSummary = "BOULEVARD TRAFFIC • PIN-PON SIREN • CAFE CROWD",
            trafficDroneFreq = 82f,
            trafficVolume = 0.18f,
            crowdDensity = 0.25f,
            crowdFormantHz = 620f,
            hornBaseFreq = 525f,
            sirenCycleSeconds = 7.5f
        ),
        City.TOKYO to CityAcousticProfile(
            city = City.TOKYO,
            surfaceName = "NEON SEALED PAVEMENT",
            ambientSummary = "SHINJUKU TRAFFIC • CROSSWALK CHIME • CROWD",
            trafficDroneFreq = 90f,
            trafficVolume = 0.20f,
            crowdDensity = 0.26f,
            crowdFormantHz = 680f,
            hornBaseFreq = 587f,
            sirenCycleSeconds = 7.0f
        ),
        City.LONDON to CityAcousticProfile(
            city = City.LONDON,
            surfaceName = "WET FLAGSTONE",
            ambientSummary = "BUS TRAFFIC • UK SIREN • WESTMINSTER CROWD",
            trafficDroneFreq = 62f,
            trafficVolume = 0.23f,
            crowdDensity = 0.19f,
            crowdFormantHz = 500f,
            hornBaseFreq = 350f,
            sirenCycleSeconds = 8.0f
        ),
        City.CAIRO to CityAcousticProfile(
            city = City.CAIRO,
            surfaceName = "SANDSTONE & GRAVEL",
            ambientSummary = "CAIRO HORNS • WARBLE SIREN • BAZAAR CROWD",
            trafficDroneFreq = 76f,
            trafficVolume = 0.22f,
            crowdDensity = 0.28f,
            crowdFormantHz = 710f,
            hornBaseFreq = 466f,
            sirenCycleSeconds = 6.8f
        ),
        City.SYDNEY to CityAcousticProfile(
            city = City.SYDNEY,
            surfaceName = "HARBOUR BOARDWALK",
            ambientSummary = "HARBOUR TRAFFIC • FERRY HORN • QUAY CROWD",
            trafficDroneFreq = 72f,
            trafficVolume = 0.17f,
            crowdDensity = 0.21f,
            crowdFormantHz = 590f,
            hornBaseFreq = 392f,
            sirenCycleSeconds = 8.5f
        )
    )

    // Pre-synthesized buffers for zero-allocation gameplay mixing
    private val leftFootstepBuffers = HashMap<City, ShortArray>()
    private val rightFootstepBuffers = HashMap<City, ShortArray>()
    private val landingBuffers = HashMap<City, ShortArray>()
    private val slideBuffers = HashMap<City, ShortArray>()
    private val ambientBurstBuffers = HashMap<Pair<City, AmbientNoiseType>, ShortArray>()

    private var jumpBuffer: ShortArray = ShortArray(0)
    private var laneSwitchBuffer: ShortArray = ShortArray(0)
    private var collectBuffer: ShortArray = ShortArray(0)
    private var shieldBreakBuffer: ShortArray = ShortArray(0)
    private var gameOverBuffer: ShortArray = ShortArray(0)

    @Volatile
    private var buffersInitialized = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? SoundManagerService.SoundServiceBinder
            boundService = binder?.getService()
            isServiceBound = true
            boundService?.startAudioEngineIfNeeded()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isServiceBound = false
        }
    }

    fun init(context: Context? = null) {
        ensureBuffersSynthesized()
        if (context != null) {
            val appCtx = context.applicationContext
            appContext = appCtx
            try {
                val intent = Intent(appCtx, SoundManagerService::class.java)
                appCtx.startService(intent)
                appCtx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            } catch (_: Exception) {
                startStandaloneLoopIfNeeded()
            }
        } else {
            startStandaloneLoopIfNeeded()
        }
    }

    internal fun attachService(service: SoundManagerService) {
        ensureBuffersSynthesized()
        boundService = service
        standaloneLoopJob?.cancel()
        standaloneLoopJob = null
    }

    internal fun detachService(service: SoundManagerService) {
        if (boundService === service) {
            boundService = null
        }
    }

    private fun startStandaloneLoopIfNeeded() {
        if (boundService != null || standaloneLoopJob?.isActive == true) return
        standaloneLoopJob = scope.launch {
            runStreamingAudioLoop { isActive && boundService == null }
        }
    }

    fun toggleMute(): Boolean {
        val next = !_isMuted.value
        _isMuted.value = next
        if (next) {
            activeVoices.clear()
        }
        return next
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        if (muted) {
            activeVoices.clear()
        }
    }

    fun getProfileForCity(city: City): CityAcousticProfile {
        return cityProfiles[city] ?: cityProfiles.getValue(City.NEW_YORK)
    }

    /**
     * Synchronizes the sound manager service with the current game environment state.
     */
    fun updateEnvironmentState(
        city: City,
        isPlaying: Boolean,
        isPaused: Boolean,
        isGameOver: Boolean,
        isSprinting: Boolean
    ) {
        val previousCity = _currentCity.value
        val wasActive = _isAmbientActive.value
        val nowActive = isPlaying && !isPaused && !isGameOver

        _currentCity.value = city
        _isAmbientActive.value = nowActive
        _isSprinting.value = isSprinting

        val profile = getProfileForCity(city)
        if (!nowActive) {
            _activeSoundStatus.value = "${city.cityName.uppercase()}: STANDBY (${profile.surfaceName})"
        } else if (previousCity != city || !wasActive) {
            _activeSoundStatus.value = "${city.cityName.uppercase()}: ${profile.ambientSummary}"
            // Trigger an introductory city traffic/crowd swell when entering a city
            triggerAmbientCityNoise(city, AmbientNoiseType.CROWD)
        }
    }

    /**
     * Triggers a character footstep sound tailored to the current city's street surface.
     */
    fun playFootstepSound(
        city: City = _currentCity.value,
        isLeftFoot: Boolean = true,
        isSprinting: Boolean = _isSprinting.value
    ) {
        ensureBuffersSynthesized()
        _footstepCount.value += 1
        if (_isMuted.value) return

        val service = boundService
        if (service != null) {
            service.triggerFootstep(city, isLeftFoot, isSprinting)
        } else {
            enqueueFootstepVoice(city, isLeftFoot, isSprinting)
        }
    }

    internal fun enqueueFootstepVoice(city: City, isLeftFoot: Boolean, isSprinting: Boolean) {
        val buffer = if (isLeftFoot) {
            leftFootstepBuffers[city]
        } else {
            rightFootstepBuffers[city]
        } ?: return

        val gain = if (isSprinting) 0.92f else 0.75f
        enqueueVoice(buffer, gain)
    }

    /**
     * Triggers a discrete ambient city sound (TRAFFIC, SIREN, or CROWD) tailored to the given city.
     */
    fun triggerAmbientCityNoise(city: City = _currentCity.value, type: AmbientNoiseType) {
        ensureBuffersSynthesized()
        _lastTriggeredAmbient.value = type
        val profile = getProfileForCity(city)
        _activeSoundStatus.value = "${city.cityName.uppercase()}: ${type.label} • ${profile.surfaceName}"

        if (_isMuted.value) return
        val service = boundService
        if (service != null) {
            service.triggerAmbientNoise(city, type)
        } else {
            enqueueAmbientBurstVoice(city, type)
        }
    }

    internal fun enqueueAmbientBurstVoice(city: City, type: AmbientNoiseType) {
        val buffer = ambientBurstBuffers[Pair(city, type)] ?: return
        val gain = when (type) {
            AmbientNoiseType.TRAFFIC -> 0.58f
            AmbientNoiseType.SIREN -> 0.48f
            AmbientNoiseType.CROWD -> 0.52f
        }
        enqueueVoice(buffer, gain)
    }

    fun playLandingSound(city: City = _currentCity.value) {
        ensureBuffersSynthesized()
        if (_isMuted.value) return
        landingBuffers[city]?.let { enqueueVoice(it, 0.9f) }
    }

    fun playSlideSound(city: City = _currentCity.value) {
        ensureBuffersSynthesized()
        if (_isMuted.value) return
        slideBuffers[city]?.let { enqueueVoice(it, 0.82f) }
    }

    fun playLaneSwitchSound() {
        ensureBuffersSynthesized()
        if (_isMuted.value) return
        enqueueVoice(laneSwitchBuffer, 0.5f)
    }

    fun playJumpSound() {
        ensureBuffersSynthesized()
        if (_isMuted.value) return
        enqueueVoice(jumpBuffer, 0.78f)
    }

    fun playCollectSound() {
        ensureBuffersSynthesized()
        if (_isMuted.value) return
        enqueueVoice(collectBuffer, 0.85f)
    }

    fun playShieldBreakSound() {
        ensureBuffersSynthesized()
        if (_isMuted.value) return
        enqueueVoice(shieldBreakBuffer, 0.88f)
    }

    fun playGameOverSound() {
        ensureBuffersSynthesized()
        if (_isMuted.value) return
        enqueueVoice(gameOverBuffer, 0.95f)
    }

    private fun enqueueVoice(samples: ShortArray, gain: Float) {
        if (samples.isEmpty()) return
        while (activeVoices.size >= 10) {
            activeVoices.poll()
        }
        activeVoices.offer(ActiveVoice(samples, 0, gain))
    }

    /**
     * Real-time streaming mixer loop executed by SoundManagerService (or fallback scope).
     * Continuously mixes the city's ambient traffic rumble, crowd murmur, periodic sirens/horns,
     * and active character footsteps/SFX into a single AudioTrack stream.
     */
    internal suspend fun runStreamingAudioLoop(shouldContinue: () -> Boolean) {
        ensureBuffersSynthesized()

        var audioTrack: AudioTrack? = null
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(CHUNK_SAMPLES * 4)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()
        } catch (_: Exception) {
            // In headless unit test environments where AudioTrack is stubbed, continue loop state safely
            audioTrack = null
        }

        val mixChunk = ShortArray(CHUNK_SAMPLES)
        var phaseTime = 0.0
        val dtSample = 1.0 / SAMPLE_RATE
        var lowPassTraffic = 0f
        var bandPassCrowd = 0f
        var bandPassCrowdPrev = 0f
        var timeSinceLastAutoEvent = 0f
        var autoEventCycleIndex = 0
        var rngState = 0x13579BDF

        try {
            while (shouldContinue()) {
                val muted = _isMuted.value
                val ambientOn = _isAmbientActive.value
                val city = _currentCity.value
                val profile = getProfileForCity(city)
                val sprinting = _isSprinting.value

                if (ambientOn && !muted) {
                    timeSinceLastAutoEvent += CHUNK_SAMPLES.toFloat() / SAMPLE_RATE
                    val eventInterval = if (sprinting) 3.4f else 4.6f
                    if (timeSinceLastAutoEvent >= eventInterval) {
                        timeSinceLastAutoEvent = 0f
                        val nextType = when (autoEventCycleIndex % 3) {
                            0 -> AmbientNoiseType.TRAFFIC
                            1 -> AmbientNoiseType.SIREN
                            else -> AmbientNoiseType.CROWD
                        }
                        autoEventCycleIndex++
                        triggerAmbientCityNoise(city, nextType)
                    }
                }

                for (i in 0 until CHUNK_SAMPLES) {
                    var sampleFloat = 0f

                    if (!muted && ambientOn) {
                        // Fast deterministic xorshift noise [-1f, 1f]
                        rngState = rngState xor (rngState shl 13)
                        rngState = rngState xor (rngState ushr 17)
                        rngState = rngState xor (rngState shl 5)
                        val white = (rngState and 0x7FFF).toFloat() / 16384f - 1f

                        // 1. Continuous City Traffic Bed (engine drone + tire road noise + Doppler wave)
                        lowPassTraffic += 0.045f * (white - lowPassTraffic)
                        val sprintFreqMult = if (sprinting) 1.32f else 1.0f
                        val engineFreq = profile.trafficDroneFreq * sprintFreqMult
                        val engineHum = (sin(2.0 * PI * engineFreq * phaseTime) * 0.55 +
                            sin(2.0 * PI * engineFreq * 2.0 * phaseTime) * 0.25).toFloat()
                        val trafficWave = 0.65f + 0.35f * sin(2.0 * PI * 0.28 * phaseTime).toFloat()
                        val trafficSample = (lowPassTraffic * 0.65f + engineHum * 0.35f) *
                            profile.trafficVolume * trafficWave

                        // 2. Continuous City Crowd Murmur (syllabic formant-modulated street chatter)
                        val formantAlpha = (profile.crowdFormantHz / SAMPLE_RATE.toFloat()).coerceIn(0.05f, 0.35f)
                        bandPassCrowd += formantAlpha * (white - bandPassCrowd - bandPassCrowdPrev * 0.3f)
                        bandPassCrowdPrev = bandPassCrowd
                        val chatterEnvelope = (0.5f + 0.5f * sin(2.0 * PI * 5.2 * phaseTime) *
                            cos(2.0 * PI * 1.7 * phaseTime)).toFloat()
                        val crowdSample = bandPassCrowd * profile.crowdDensity * chatterEnvelope * 0.45f

                        // 3. Subtle City Signature Atmosphere
                        val cityAtmosphere = when (city) {
                            City.NEW_YORK -> {
                                // Distant subway/avenue harmonic rumble
                                sin(2.0 * PI * 55.0 * phaseTime).toFloat() * 0.035f
                            }
                            City.PARIS -> {
                                // Warm boulevard accordion-like fifth harmonic shimmer
                                (sin(2.0 * PI * 330.0 * phaseTime) + sin(2.0 * PI * 495.0 * phaseTime)).toFloat() *
                                    0.012f * (0.5f + 0.5f * sin(2.0 * PI * 0.4 * phaseTime).toFloat())
                            }
                            City.TOKYO -> {
                                // Subtle neon transformer hum + high electronic pulse
                                val pulse = if (((phaseTime * 2.0).toInt() % 4) == 0) 0.018f else 0f
                                sin(2.0 * PI * 1396.9 * phaseTime).toFloat() * pulse
                            }
                            City.LONDON -> {
                                // Rain-slick tire hiss on wet tarmac
                                white * 0.022f * (0.6f + 0.4f * sin(2.0 * PI * 0.5 * phaseTime).toFloat())
                            }
                            City.CAIRO -> {
                                // Warm desert wind swirl
                                lowPassTraffic * 0.045f * sin(2.0 * PI * 0.19 * phaseTime).toFloat()
                            }
                            City.SYDNEY -> {
                                // Rhythmic harbour wave wash
                                white * 0.03f * (0.5f + 0.5f * sin(2.0 * PI * 0.22 * phaseTime).toFloat())
                            }
                        }

                        sampleFloat += trafficSample + crowdSample + cityAtmosphere
                    }

                    // 4. Mix active one-shot voices (footsteps, sirens, horns, crowd bursts, jumps)
                    if (!muted && activeVoices.isNotEmpty()) {
                        val iterator = activeVoices.iterator()
                        while (iterator.hasNext()) {
                            val voice = iterator.next()
                            val pos = voice.position
                            if (pos < voice.samples.size) {
                                sampleFloat += (voice.samples[pos] / 32768f) * voice.gain
                                voice.position = pos + 1
                            } else {
                                iterator.remove()
                            }
                        }
                    }

                    // Soft clip limiter to prevent digital distortion when multiple sounds overlap
                    val clamped = (sampleFloat / (1f + abs(sampleFloat) * 0.35f)).coerceIn(-0.98f, 0.98f)
                    mixChunk[i] = (clamped * 32767f).toInt().toShort()
                    phaseTime += dtSample
                }

                if (audioTrack != null) {
                    val written = audioTrack.write(mixChunk, 0, CHUNK_SAMPLES)
                    if (written < 0) {
                        delay(20)
                    }
                } else {
                    delay(20)
                }
            }
        } finally {
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized
    private fun ensureBuffersSynthesized() {
        if (buffersInitialized) return
        for (city in City.entries) {
            leftFootstepBuffers[city] = synthesizeFootstep(city, isLeftFoot = true)
            rightFootstepBuffers[city] = synthesizeFootstep(city, isLeftFoot = false)
            landingBuffers[city] = synthesizeLanding(city)
            slideBuffers[city] = synthesizeSlide(city)
            ambientBurstBuffers[Pair(city, AmbientNoiseType.TRAFFIC)] = synthesizeTrafficBurst(city)
            ambientBurstBuffers[Pair(city, AmbientNoiseType.SIREN)] = synthesizeSirenBurst(city)
            ambientBurstBuffers[Pair(city, AmbientNoiseType.CROWD)] = synthesizeCrowdBurst(city)
        }
        jumpBuffer = synthesizeJump()
        laneSwitchBuffer = synthesizeLaneSwitch()
        collectBuffer = synthesizeCollect()
        shieldBreakBuffer = synthesizeShieldBreak()
        gameOverBuffer = synthesizeGameOver()
        buffersInitialized = true
    }

    /**
     * Synthesizes a realistic character footstep sound tailored to each city's ground material.
     */
    internal fun synthesizeFootstep(city: City, isLeftFoot: Boolean): ShortArray {
        val durationMs = 115
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        val pitchShift = if (isLeftFoot) 0.96f else 1.04f
        val rng = Random(city.ordinal * 31 + if (isLeftFoot) 1 else 2)

        var filterState = 0f
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / numSamples
            val noise = rng.nextFloat() * 2f - 1f

            val sample = when (city) {
                City.NEW_YORK -> {
                    // Heavy urban asphalt: punchy rubber heel-strike + gritty asphalt scuff + canyon slapback
                    val heelThump = sin(2.0 * PI * (115f * pitchShift) * t * exp(-t * 28.0)).toFloat() * exp(-t * 42f)
                    filterState += 0.38f * (noise - filterState)
                    val asphaltGrit = (noise - filterState) * exp(-t * 55f) * 0.55f
                    val toePush = if (t > 0.032f) {
                        val t2 = t - 0.032f
                        (noise * 0.35f + sin(2.0 * PI * 210f * t2).toFloat() * 0.25f) * exp(-t2 * 65f)
                    } else 0f
                    heelThump * 0.75f + asphaltGrit + toePush
                }
                City.PARIS -> {
                    // Haussmann cobblestone pavers: sharp resonant stone knock + crisp mineral click
                    val stoneClick = (sin(2.0 * PI * (245f * pitchShift) * t) +
                        0.5 * sin(2.0 * PI * (590f * pitchShift) * t)).toFloat() * exp(-t * 52f)
                    val cobbleTap = if (t > 0.025f) {
                        val t2 = t - 0.025f
                        sin(2.0 * PI * (420f * pitchShift) * t2).toFloat() * exp(-t2 * 75f) * 0.45f
                    } else 0f
                    val scuff = noise * exp(-t * 80f) * 0.32f
                    stoneClick * 0.7f + cobbleTap + scuff
                }
                City.TOKYO -> {
                    // Smooth sealed neon pavement: tight rubber outsole snap + subtle high-sheen squeak
                    val rubberSnap = sin(2.0 * PI * (145f * pitchShift) * t * exp(-t * 22.0)).toFloat() * exp(-t * 48f)
                    val squeakPartial = sin(2.0 * PI * (2650f * pitchShift) * t).toFloat() *
                        exp(-abs(t - 0.028f) * 120f) * 0.18f
                    val crispTap = noise * exp(-t * 70f) * 0.38f
                    rubberSnap * 0.72f + squeakPartial + crispTap
                }
                City.LONDON -> {
                    // Damp flagstone & rain-slick tarmac: deep stone thud + wet water-spray tail
                    val deepFlagstone = sin(2.0 * PI * (92f * pitchShift) * t * exp(-t * 24.0)).toFloat() * exp(-t * 38f)
                    filterState += 0.18f * (noise - filterState)
                    val highSplash = (noise - filterState) * exp(-t * 26f) * 0.58f
                    deepFlagstone * 0.7f + highSplash
                }
                City.CAIRO -> {
                    // Dry sandstone & desert gravel: warm muted earth thump + multi-grain sand crunch
                    val earthThump = sin(2.0 * PI * (128f * pitchShift) * t * exp(-t * 30.0)).toFloat() * exp(-t * 44f)
                    val grainburst = (0.5f + 0.5f * sin(2.0 * PI * 95f * t).toFloat())
                    val sandCrunch = noise * grainburst * exp(-t * 32f) * 0.62f
                    earthThump * 0.62f + sandCrunch
                }
                City.SYDNEY -> {
                    // Harbourside boardwalk & sandstone: hollow woody timber/sandstone resonance + crisp sneaker tap
                    val woodyResonance = (sin(2.0 * PI * (162f * pitchShift) * t) +
                        0.45 * sin(2.0 * PI * (335f * pitchShift) * t)).toFloat() * exp(-t * 40f)
                    val deckTap = noise * exp(-t * 62f) * 0.36f
                    woodyResonance * 0.72f + deckTap
                }
            }

            val fadeOut = (1f - progress).coerceIn(0f, 1f)
            out[i] = ((sample * fadeOut).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthesizeLanding(city: City): ShortArray {
        val step1 = synthesizeFootstep(city, isLeftFoot = true)
        val step2 = synthesizeFootstep(city, isLeftFoot = false)
        val durationSamples = (SAMPLE_RATE * 180) / 1000
        val out = ShortArray(durationSamples)
        val offsetSecondFoot = (SAMPLE_RATE * 38) / 1000

        for (i in 0 until durationSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val subImpact = sin(2.0 * PI * 75.0 * t * exp(-t * 18.0)).toFloat() * exp(-t * 25f) * 0.6f
            val s1 = if (i < step1.size) step1[i] / 32768f else 0f
            val s2 = if (i >= offsetSecondFoot && (i - offsetSecondFoot) < step2.size) {
                step2[i - offsetSecondFoot] / 32768f
            } else 0f
            out[i] = ((subImpact + s1 * 0.85f + s2 * 0.85f).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthesizeSlide(city: City): ShortArray {
        val durationMs = 340
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        val rng = Random(city.ordinal + 99)
        var lp = 0f
        val filterAlpha = when (city) {
            City.CAIRO -> 0.45f
            City.LONDON -> 0.55f
            City.PARIS -> 0.35f
            else -> 0.28f
        }
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / numSamples
            val env = sin(PI * progress).toFloat() * exp(-t * 3.5f)
            val noise = rng.nextFloat() * 2f - 1f
            lp += filterAlpha * (noise - lp)
            val friction = (noise * 0.55f + lp * 0.45f) * env * 0.65f
            out[i] = (friction.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    /**
     * Synthesizes city-authentic traffic bursts (cab horns, bus rumbles, crosswalk chimes, ferry horns, Doppler pass-bys).
     */
    internal fun synthesizeTrafficBurst(city: City): ShortArray {
        val durationMs = 620
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        val profile = getProfileForCity(city)
        val rng = Random(city.ordinal + 500)
        var lp = 0f

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / numSamples
            val noise = rng.nextFloat() * 2f - 1f
            lp += 0.12f * (noise - lp)

            // Doppler pass-by whoosh envelope
            val dopplerEnv = sin(PI * progress).toFloat()
            val dopplerShift = 1.25f - 0.5f * progress

            val cityHorn = when (city) {
                City.NEW_YORK -> {
                    // Iconic NYC yellow cab double-honk (two brassy discordant horn blasts)
                    val honkOn = (t in 0.04f..0.19f) || (t in 0.25f..0.48f)
                    if (honkOn) {
                        val f1 = profile.hornBaseFreq * dopplerShift
                        val f2 = f1 * 1.24f // Major third / tritone cab horn interval
                        (sin(2.0 * PI * f1 * t) * 0.45 +
                            sin(2.0 * PI * f2 * t) * 0.45 +
                            sin(2.0 * PI * f1 * 2.0 * t) * 0.22).toFloat() * 0.55f
                    } else 0f
                }
                City.PARIS -> {
                    // Crisp European compact / scooter triple-beep horn
                    val honkOn = (t in 0.05f..0.15f) || (t in 0.20f..0.38f)
                    if (honkOn) {
                        val f1 = profile.hornBaseFreq
                        val f2 = f1 * 1.26f
                        (sin(2.0 * PI * f1 * t) + sin(2.0 * PI * f2 * t)).toFloat() * 0.35f
                    } else 0f
                }
                City.TOKYO -> {
                    // Authentic Japanese crosswalk "Piyo-Piyo" bird chime + sleek electric car pass-by
                    val piyo1 = if (t in 0.04f..0.16f) {
                        val sweep = 1760f - (t - 0.04f) * 3200f
                        sin(2.0 * PI * sweep * t).toFloat() * 0.45f
                    } else 0f
                    val piyo2 = if (t in 0.22f..0.35f) {
                        val sweep = 1560f - (t - 0.22f) * 2800f
                        sin(2.0 * PI * sweep * t).toFloat() * 0.45f
                    } else 0f
                    piyo1 + piyo2
                }
                City.LONDON -> {
                    // Deep double-decker diesel bus horn & low engine acceleration
                    val busHorn = if (t in 0.08f..0.44f) {
                        val f1 = 285f * dopplerShift
                        val f2 = 356f * dopplerShift
                        (sin(2.0 * PI * f1 * t) + sin(2.0 * PI * f2 * t) * 0.8).toFloat() * 0.38f
                    } else 0f
                    val busRumble = sin(2.0 * PI * (68f * dopplerShift) * t).toFloat() * dopplerEnv * 0.35f
                    busHorn + busRumble
                }
                City.CAIRO -> {
                    // Lively Cairo staccato rhythmic taxi honks (beep-beep-beeeeep)
                    val honkOn = (t in 0.03f..0.11f) || (t in 0.15f..0.23f) || (t in 0.28f..0.48f)
                    if (honkOn) {
                        val f1 = profile.hornBaseFreq
                        (sin(2.0 * PI * f1 * t) + 0.5 * sin(2.0 * PI * f1 * 1.5 * t)).toFloat() * 0.42f
                    } else 0f
                }
                City.SYDNEY -> {
                    // Deep Sydney Harbour ferry foghorn + coastal road pass-by
                    val foghornEnv = sin(PI * progress).toFloat()
                    val f1 = 116.5f
                    (sin(2.0 * PI * f1 * t) * 0.55 +
                        sin(2.0 * PI * f1 * 1.5 * t) * 0.3 +
                        sin(2.0 * PI * f1 * 2.0 * t) * 0.15).toFloat() * foghornEnv * 0.55f
                }
            }

            val vehiclePassBy = lp * dopplerEnv * 0.42f
            out[i] = ((cityHorn + vehiclePassBy).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    /**
     * Synthesizes city-authentic emergency sirens & acoustic bells.
     */
    internal fun synthesizeSirenBurst(city: City): ShortArray {
        val durationMs = 780
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / numSamples
            // Smooth distance fade-in and fade-out envelope
            val distanceEnv = sin(PI * progress).toFloat()

            val instFreq = when (city) {
                City.NEW_YORK -> {
                    // NYPD / FDNY continuous sweeping wail siren (620Hz <-> 1340Hz)
                    620.0 + 720.0 * (0.5 + 0.5 * sin(2.0 * PI * 1.6 * t - PI / 2.0))
                }
                City.PARIS -> {
                    // French "Pin-Pon" two-tone alternating siren (435Hz / 580Hz)
                    if (((t * 3.2f).toInt() % 2) == 0) 435.0 else 580.0
                }
                City.TOKYO -> {
                    // Japanese "Peepo-Peepo" high two-tone emergency siren (960Hz / 770Hz)
                    if (((t * 3.0f).toInt() % 2) == 0) 960.0 else 770.0
                }
                City.LONDON -> {
                    // British emergency pulsar siren alternating rapid two-tone (650Hz / 820Hz)
                    if (((t * 4.5f).toInt() % 2) == 0) 650.0 else 820.0
                }
                City.CAIRO -> {
                    // Middle Eastern rapid warble siren (720Hz - 1120Hz at 8Hz warble)
                    720.0 + 400.0 * (0.5 + 0.5 * sin(2.0 * PI * 8.5 * t))
                }
                City.SYDNEY -> {
                    // Australian fast yelp/wail emergency siren (580Hz - 1250Hz)
                    580.0 + 670.0 * abs(sin(2.0 * PI * 2.2 * t))
                }
            }

            phase += (2.0 * PI * instFreq) / SAMPLE_RATE
            val sirenTone = (sin(phase) * 0.75 + sin(phase * 2.0) * 0.25).toFloat() * distanceEnv * 0.48f
            out[i] = (sirenTone.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    /**
     * Synthesizes city-authentic crowd murmur swells and street life cues.
     */
    internal fun synthesizeCrowdBurst(city: City): ShortArray {
        val durationMs = 680
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        val profile = getProfileForCity(city)
        val rng = Random(city.ordinal + 900)
        var bp1 = 0f
        var bp2 = 0f

        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / numSamples
            val swellEnv = sin(PI * progress).toFloat()
            val noise = rng.nextFloat() * 2f - 1f

            // Multi-formant vocal crowd murmur
            val alpha1 = (profile.crowdFormantHz / SAMPLE_RATE).coerceIn(0.06f, 0.32f)
            bp1 += alpha1 * (noise - bp1)
            bp2 += (alpha1 * 1.6f).coerceAtMost(0.45f) * (noise - bp2)
            val syllableMod = (0.55f + 0.45f * sin(2.0 * PI * 6.5 * t) * cos(2.0 * PI * 2.3 * t)).toFloat()
            val crowdChatter = (bp1 * 0.6f + (bp2 - bp1) * 0.4f) * syllableMod * swellEnv * 0.62f

            // City-specific street life accent on top of the crowd
            val streetAccent = when (city) {
                City.NEW_YORK -> {
                    // Street vendor whistle / taxi hail whistle
                    if (t in 0.15f..0.38f) {
                        val f = 1850f + (t - 0.15f) * 1400f
                        sin(2.0 * PI * f * t).toFloat() * 0.18f * swellEnv
                    } else 0f
                }
                City.PARIS -> {
                    // Parisian cafe musette accordion harmonic dyad
                    val f1 = 440.0
                    val f2 = 554.37
                    val tremolo = 1.0 + 0.25 * sin(2.0 * PI * 5.5 * t)
                    ((sin(2.0 * PI * f1 * t) + sin(2.0 * PI * f2 * t)) * tremolo).toFloat() * 0.14f * swellEnv
                }
                City.TOKYO -> {
                    // Shibuya arcade/station two-note bright chime
                    val f = if (t < 0.32f) 1046.5 else 1318.5
                    sin(2.0 * PI * f * t).toFloat() * exp(-(t % 0.32f) * 9f) * 0.20f * swellEnv
                }
                City.LONDON -> {
                    // Distant Big Ben Westminster bell strike harmonic
                    val f = 329.63 // E4 bell
                    (sin(2.0 * PI * f * t) + 0.5 * sin(2.0 * PI * f * 2.0 * t) + 0.25 * sin(2.0 * PI * f * 2.98 * t)).toFloat() *
                        exp(-t * 4.5f) * 0.22f
                }
                City.CAIRO -> {
                    // Khan el-Khalili rhythmic bazaar hand-drum / darbuka pulse
                    val beatT = t % 0.22f
                    sin(2.0 * PI * 145.0 * beatT * exp(-beatT * 14.0)).toFloat() * exp(-beatT * 24f) * 0.28f * swellEnv
                }
                City.SYDNEY -> {
                    // Circular Quay seagull cry over harbour crowd
                    if (t in 0.12f..0.42f) {
                        val localT = (t - 0.12f) / 0.30f
                        val gullFreq = 1450f + 420f * sin(PI * localT).toFloat() - 300f * localT
                        sin(2.0 * PI * gullFreq * t).toFloat() * sin(PI * localT).toFloat() * 0.18f
                    } else 0f
                }
            }

            out[i] = ((crowdChatter + streetAccent).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthesizeJump(): ShortArray {
        val durationMs = 150
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / numSamples
            val freq = 260.0 + 380.0 * progress * progress
            phase += (2.0 * PI * freq) / SAMPLE_RATE
            val env = (1f - progress) * (0.4f + 0.6f * sin(PI * progress).toFloat())
            out[i] = (sin(phase).toFloat() * env * 0.7f * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthesizeLaneSwitch(): ShortArray {
        val durationMs = 85
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        val rng = Random(77)
        var bp = 0f
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val env = sin(PI * progress).toFloat()
            val noise = rng.nextFloat() * 2f - 1f
            bp += 0.25f * (noise - bp)
            val whoosh = bp * env * 0.55f
            out[i] = (whoosh.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthesizeCollect(): ShortArray {
        val durationMs = 240
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / numSamples
            val freq = when {
                progress < 0.33f -> 587.33 // D5
                progress < 0.66f -> 880.00 // A5
                else -> 1174.66 // D6
            }
            val localEnv = exp(-((progress % 0.33f) * 8f))
            val sample = (sin(2.0 * PI * freq * t) * 0.7 + sin(2.0 * PI * freq * 2.0 * t) * 0.3).toFloat() *
                localEnv * (1f - progress * 0.4f)
            out[i] = ((sample * 0.75f).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthesizeShieldBreak(): ShortArray {
        val durationMs = 280
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        val rng = Random(303)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 14f)
            val shimmer = (sin(2.0 * PI * 1480.0 * t) + sin(2.0 * PI * 2120.0 * t) + (rng.nextFloat() - 0.5f)).toFloat() * env * 0.35f
            out[i] = (shimmer.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthesizeGameOver(): ShortArray {
        val durationMs = 520
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(numSamples)
        var phase = 0.0
        val rng = Random(404)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / numSamples
            val freq = 240.0 * exp(-progress * 1.1)
            phase += (2.0 * PI * freq) / SAMPLE_RATE
            val impactNoise = (rng.nextFloat() * 2f - 1f) * exp(-t * 28f) * 0.5f
            val subDrop = (sin(phase) * 0.7 + sin(phase * 0.5) * 0.3).toFloat() * (1f - progress)
            out[i] = ((impactNoise + subDrop * 0.7f).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    fun release() {
        _isAmbientActive.value = false
        activeVoices.clear()
        standaloneLoopJob?.cancel()
        standaloneLoopJob = null
        val ctx = appContext
        if (ctx != null && isServiceBound) {
            try {
                ctx.unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            isServiceBound = false
        }
        boundService = null
    }
}
