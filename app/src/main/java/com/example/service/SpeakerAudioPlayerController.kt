package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.local.AudioClipEntity
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SpeakerOutputRoute(val label: String, val hint: String) {
  ROOT_CALL_INJECT(
    label = "Injeksi Root (su)",
    hint = "Jalankan AudioTrack sebagai root (uid=0) langsung ke jalur komunikasi WhatsApp",
  ),
  MEDIA_SPEAKER(
    label = "Speaker Lantang",
    hint = "Putar ke speaker utama + booster agar tertangkap jelas oleh mic WhatsApp",
  ),
  VOICE_CALL_SPEAKER(
    label = "Jalur Panggilan WA",
    hint = "Gunakan stream komunikasi suara (cadangan jika HP mengecilkan media saat telepon)",
  ),
}

data class PickedAudioMeta(
  val originalFileName: String,
  val suggestedName: String,
  val sizeBytes: Long,
)

data class RecordedAudioDraft(
  val file: File,
  val durationMs: Long,
  val fileSizeBytes: Long,
)

data class AudioPlaybackState(
  val activeClipId: Long? = null,
  val activeClipName: String = "",
  val isPlaying: Boolean = false,
  val isPaused: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val speakerphoneEnabled: Boolean = true,
  val loopEnabled: Boolean = false,
  val voiceBoostEnabled: Boolean = true,
  val autoUnmuteMicEnabled: Boolean = true,
  val outputRoute: SpeakerOutputRoute = SpeakerOutputRoute.MEDIA_SPEAKER,
  val micTemporarilyUnmuted: Boolean = false,
  val rootDiagnostics: RootAudioDiagnostics = RootAudioDiagnostics(),
  val activeRouteDescription: String = "",
  val errorMessage: String? = null,
)

data class AudioRecordingState(
  val isRecording: Boolean = false,
  val elapsedMs: Long = 0L,
  val errorMessage: String? = null,
)

class SpeakerAudioPlayerController(
  context: Context,
  private val scope: CoroutineScope,
  private val stealthAudioController: StealthAudioController? = null,
) {

  private val appContext = context.applicationContext
  private val audioManager =
    appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val rootAudioBridge = RootAudioBridge(appContext, scope)

  private val _playbackState = MutableStateFlow(AudioPlaybackState())
  val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

  private val _recordingState = MutableStateFlow(AudioRecordingState())
  val recordingState: StateFlow<AudioRecordingState> = _recordingState.asStateFlow()

  private var mediaPlayer: MediaPlayer? = null
  private var loudnessEnhancer: LoudnessEnhancer? = null
  private var activeClipEntity: AudioClipEntity? = null
  private var isRootPlaybackActive: Boolean = false
  private var rootPlayJob: Job? = null
  private var progressJob: Job? = null
  private var previousSpeakerphoneState: Boolean? = null
  private var appliedCommunicationSpeaker: Boolean = false
  private var adjustedStreamType: Int? = null
  private var previousStreamVolume: Int? = null

  private var mediaRecorder: MediaRecorder? = null
  private var recordingFile: File? = null
  private var recordingStartedAtMs: Long = 0L
  private var recordingTimerJob: Job? = null

  fun checkRootAccess() {
    scope.launch {
      val diag = rootAudioBridge.probeRootDiagnostics()
      val updatedRoute =
        if (diag.isRootGranted && _playbackState.value.outputRoute == SpeakerOutputRoute.MEDIA_SPEAKER) {
          SpeakerOutputRoute.ROOT_CALL_INJECT
        } else {
          _playbackState.value.outputRoute
        }
      _playbackState.value =
        _playbackState.value.copy(rootDiagnostics = diag, outputRoute = updatedRoute)
    }
  }

  fun startRecording(): Boolean {
    stopInternal(keepError = false)
    cancelRecordingInternal()

    val dir = File(appContext.filesDir, "audio_clips").apply { mkdirs() }
    val outFile = File(dir, "rec_${UUID.randomUUID()}.m4a")

    return try {
      val recorder = createConfiguredRecorder(outFile, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        ?: createConfiguredRecorder(outFile, MediaRecorder.AudioSource.MIC)
        ?: throw IllegalStateException("Tidak dapat menginisialisasi mikrofon.")
      mediaRecorder = recorder
      recordingFile = outFile
      recordingStartedAtMs = System.currentTimeMillis()
      _recordingState.value = AudioRecordingState(isRecording = true, elapsedMs = 0L, errorMessage = null)
      startRecordingTimer()
      true
    } catch (e: Exception) {
      runCatching { outFile.delete() }
      mediaRecorder = null
      recordingFile = null
      _recordingState.value =
        AudioRecordingState(
          isRecording = false,
          elapsedMs = 0L,
          errorMessage = e.message ?: "Gagal memulai perekaman audio.",
        )
      false
    }
  }

  private fun createConfiguredRecorder(outFile: File, audioSource: Int): MediaRecorder? {
    var recorder: MediaRecorder? = null
    return try {
      recorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          MediaRecorder(appContext)
        } else {
          @Suppress("DEPRECATION") MediaRecorder()
        }
      recorder.apply {
        setAudioSource(audioSource)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioSamplingRate(44_100)
        setAudioEncodingBitRate(160_000)
        setOutputFile(outFile.absolutePath)
        prepare()
        start()
      }
      recorder
    } catch (_: Exception) {
      runCatching { recorder?.reset() }
      runCatching { recorder?.release() }
      null
    }
  }

  fun stopRecordingAndGetDraft(): RecordedAudioDraft? {
    recordingTimerJob?.cancel()
    recordingTimerJob = null
    val recorder = mediaRecorder
    val outFile = recordingFile
    val elapsed = (System.currentTimeMillis() - recordingStartedAtMs).coerceAtLeast(0L)

    mediaRecorder = null
    recordingFile = null

    if (recorder == null || outFile == null) {
      _recordingState.value = AudioRecordingState()
      return null
    }

    val stoppedCleanly =
      runCatching {
          recorder.stop()
          true
        }
        .getOrDefault(false)
    runCatching { recorder.reset() }
    runCatching { recorder.release() }

    _recordingState.value = AudioRecordingState(isRecording = false, elapsedMs = 0L, errorMessage = null)

    if (!stoppedCleanly || !outFile.exists() || outFile.length() <= 0L) {
      runCatching { outFile.delete() }
      _recordingState.value =
        AudioRecordingState(
          isRecording = false,
          elapsedMs = 0L,
          errorMessage = "Rekaman terlalu singkat atau gagal disimpan.",
        )
      return null
    }

    val probedDuration = probeDurationMs(outFile)
    val duration = if (probedDuration > 0L) probedDuration else elapsed
    return RecordedAudioDraft(
      file = outFile,
      durationMs = duration,
      fileSizeBytes = outFile.length(),
    )
  }

  fun cancelRecording() {
    cancelRecordingInternal()
    _recordingState.value = AudioRecordingState()
  }

  private fun cancelRecordingInternal() {
    recordingTimerJob?.cancel()
    recordingTimerJob = null
    runCatching {
      mediaRecorder?.let { rec ->
        runCatching { rec.stop() }
        rec.reset()
        rec.release()
      }
    }
    mediaRecorder = null
    recordingFile?.let { file -> runCatching { file.delete() } }
    recordingFile = null
  }

  private fun startRecordingTimer() {
    recordingTimerJob?.cancel()
    recordingTimerJob =
      scope.launch {
        while (isActive && _recordingState.value.isRecording) {
          val elapsed = (System.currentTimeMillis() - recordingStartedAtMs).coerceAtLeast(0L)
          _recordingState.value = _recordingState.value.copy(elapsedMs = elapsed)
          delay(200L)
        }
      }
  }

  fun playClip(clip: AudioClipEntity, startPositionMs: Long = 0L) {
    if (_recordingState.value.isRecording) {
      cancelRecording()
    }
    val file = File(clip.filePath)
    if (!file.exists() || file.length() <= 0L) {
      _playbackState.value =
        _playbackState.value.copy(
          activeClipId = clip.id,
          activeClipName = clip.name,
          isPlaying = false,
          isPaused = false,
          errorMessage = "File audio \"${clip.name}\" tidak ditemukan.",
        )
      return
    }

    stopInternal(keepError = false)
    activeClipEntity = clip
    val current = _playbackState.value

    if (current.outputRoute == SpeakerOutputRoute.ROOT_CALL_INJECT) {
      playClipViaRoot(clip, file, startPositionMs)
      return
    }

    playClipViaMediaPlayer(clip, file, startPositionMs, fallbackNotice = null)
  }

  private fun playClipViaRoot(
    clip: AudioClipEntity,
    file: File,
    startPositionMs: Long,
  ) {
    rootPlayJob?.cancel()
    rootPlayJob =
      scope.launch {
        val currentDiag = _playbackState.value.rootDiagnostics
        val diag =
          if (currentDiag.isChecked) currentDiag
          else {
            val probed = rootAudioBridge.probeRootDiagnostics()
            _playbackState.value = _playbackState.value.copy(rootDiagnostics = probed)
            probed
          }

        if (!diag.isRootGranted) {
          _playbackState.value =
            _playbackState.value.copy(outputRoute = SpeakerOutputRoute.MEDIA_SPEAKER)
          playClipViaMediaPlayer(
            clip = clip,
            file = file,
            startPositionMs = startPositionMs,
            fallbackNotice = "Akses root (su) tidak tersedia — beralih ke Speaker Lantang.",
          )
          return@launch
        }

        val wavInfo = PcmAudioDecoder.decodeToWav(file)
        if (wavInfo == null) {
          playClipViaMediaPlayer(
            clip = clip,
            file = file,
            startPositionMs = startPositionMs,
            fallbackNotice = "Gagal decode PCM untuk mode root — memakai pemutar standar.",
          )
          return@launch
        }

        val stateSnapshot = _playbackState.value
        val unmutedForWa =
          stealthAudioController?.prepareMicForSpeakerPlayback(stateSnapshot.autoUnmuteMicEnabled)
            ?: false
        applySpeakerRouting(stateSnapshot.speakerphoneEnabled)
        ensureLoudPlaybackVolume(SpeakerOutputRoute.VOICE_CALL_SPEAKER)

        isRootPlaybackActive = true
        val gainMb = if (stateSnapshot.voiceBoostEnabled) VOICE_BOOST_TARGET_GAIN_MB else 0
        val launched =
          rootAudioBridge.startRootPlayback(
            wavFile = wavInfo.wavFile,
            loopEnabled = stateSnapshot.loopEnabled,
            gainMb = gainMb,
            startPositionMs = startPositionMs,
            onStarted = { routeDesc, totalDur ->
              val resolvedDur = totalDur.coerceAtLeast(clip.durationMs)
              _playbackState.value =
                _playbackState.value.copy(
                  activeClipId = clip.id,
                  activeClipName = clip.name,
                  isPlaying = true,
                  isPaused = false,
                  positionMs = startPositionMs.coerceIn(0L, resolvedDur.coerceAtLeast(0L)),
                  durationMs = resolvedDur,
                  micTemporarilyUnmuted = unmutedForWa,
                  activeRouteDescription = routeDesc,
                  errorMessage = null,
                )
            },
            onProgress = { posMs, totalDur ->
              _playbackState.value =
                _playbackState.value.copy(
                  positionMs = posMs.coerceAtLeast(0L),
                  durationMs = totalDur.coerceAtLeast(_playbackState.value.durationMs),
                )
            },
            onCompleted = {
              if (isRootPlaybackActive) {
                stopInternal(keepError = false)
              }
            },
            onError = { errMsg ->
              if (isRootPlaybackActive) {
                isRootPlaybackActive = false
                restoreAudioEnvironment()
                _playbackState.value =
                  _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    micTemporarilyUnmuted = false,
                    activeRouteDescription = "",
                    errorMessage = errMsg,
                  )
              }
            },
          )

        if (!launched) {
          isRootPlaybackActive = false
          restoreAudioEnvironment()
        }
      }
  }

  private fun playClipViaMediaPlayer(
    clip: AudioClipEntity,
    file: File,
    startPositionMs: Long,
    fallbackNotice: String?,
  ) {
    val current = _playbackState.value
    val unmutedForWa =
      stealthAudioController?.prepareMicForSpeakerPlayback(current.autoUnmuteMicEnabled) ?: false

    applySpeakerRouting(current.speakerphoneEnabled)
    ensureLoudPlaybackVolume(current.outputRoute)

    try {
      val audioAttributes = buildAudioAttributes(current.outputRoute)
      val player =
        MediaPlayer().apply {
          setAudioAttributes(audioAttributes)
          setDataSource(file.absolutePath)
          setVolume(1.0f, 1.0f)
          isLooping = current.loopEnabled
          if (current.speakerphoneEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            findBuiltInSpeakerDevice()?.let { speaker ->
              runCatching { setPreferredDevice(speaker) }
            }
          }
          setOnCompletionListener {
            if (!_playbackState.value.loopEnabled && !isLooping) {
              stopInternal(keepError = false)
            }
          }
          setOnErrorListener { _, what, extra ->
            _playbackState.value =
              _playbackState.value.copy(
                isPlaying = false,
                isPaused = false,
                micTemporarilyUnmuted = false,
                activeRouteDescription = "",
                errorMessage = "Gagal memutar audio ($what/$extra).",
              )
            stopInternal(keepError = true)
            true
          }
          prepare()
          if (startPositionMs > 0L) {
            val dur = duration.toLong().coerceAtLeast(0L)
            if (dur > 0L) {
              seekTo(startPositionMs.coerceIn(0L, dur).toInt())
            }
          }
          start()
        }
      mediaPlayer = player
      attachOrUpdateLoudnessEnhancer(player.audioSessionId, current.voiceBoostEnabled)
      val resolvedDuration = player.duration.toLong().coerceAtLeast(clip.durationMs)
      _playbackState.value =
        current.copy(
          activeClipId = clip.id,
          activeClipName = clip.name,
          isPlaying = true,
          isPaused = false,
          positionMs = startPositionMs.coerceIn(0L, resolvedDuration.coerceAtLeast(0L)),
          durationMs = resolvedDuration,
          micTemporarilyUnmuted = unmutedForWa,
          activeRouteDescription = current.outputRoute.label,
          errorMessage = fallbackNotice,
        )
      startProgressTicker()
    } catch (e: Exception) {
      restoreAudioEnvironment()
      _playbackState.value =
        current.copy(
          activeClipId = clip.id,
          activeClipName = clip.name,
          isPlaying = false,
          isPaused = false,
          micTemporarilyUnmuted = false,
          activeRouteDescription = "",
          errorMessage = e.message ?: "Tidak dapat memutar file audio ini.",
        )
    }
  }

  fun togglePlayPause(clip: AudioClipEntity) {
    val state = _playbackState.value
    if (state.activeClipId == clip.id && (mediaPlayer != null || isRootPlaybackActive || state.isPaused)) {
      if (state.isPlaying) {
        pause()
      } else if (state.isPaused) {
        resume()
      } else {
        playClip(clip)
      }
    } else {
      playClip(clip)
    }
  }

  fun pause() {
    if (isRootPlaybackActive) {
      rootPlayJob?.cancel()
      isRootPlaybackActive = false
      rootAudioBridge.stopRootPlayback()
      restoreAudioEnvironment()
      _playbackState.value =
        _playbackState.value.copy(
          isPlaying = false,
          isPaused = true,
          micTemporarilyUnmuted = false,
        )
      return
    }
    val player = mediaPlayer ?: return
    runCatching {
      if (player.isPlaying) {
        player.pause()
      }
      val pos = player.currentPosition.toLong().coerceAtLeast(0L)
      progressJob?.cancel()
      releaseLoudnessEnhancer()
      restoreAudioEnvironment()
      _playbackState.value =
        _playbackState.value.copy(
          isPlaying = false,
          isPaused = true,
          positionMs = pos,
          micTemporarilyUnmuted = false,
        )
    }
  }

  fun resume() {
    if (_playbackState.value.outputRoute == SpeakerOutputRoute.ROOT_CALL_INJECT) {
      val clip = activeClipEntity ?: return
      playClip(clip, startPositionMs = _playbackState.value.positionMs)
      return
    }
    val player = mediaPlayer ?: return
    val current = _playbackState.value
    runCatching {
      val unmutedForWa =
        stealthAudioController?.prepareMicForSpeakerPlayback(current.autoUnmuteMicEnabled) ?: false
      applySpeakerRouting(current.speakerphoneEnabled)
      ensureLoudPlaybackVolume(current.outputRoute)
      if (current.speakerphoneEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        findBuiltInSpeakerDevice()?.let { speaker ->
          runCatching { player.setPreferredDevice(speaker) }
        }
      }
      player.setVolume(1.0f, 1.0f)
      player.start()
      attachOrUpdateLoudnessEnhancer(player.audioSessionId, current.voiceBoostEnabled)
      _playbackState.value =
        current.copy(
          isPlaying = true,
          isPaused = false,
          micTemporarilyUnmuted = unmutedForWa,
          activeRouteDescription = current.outputRoute.label,
          errorMessage = null,
        )
      startProgressTicker()
    }
  }

  fun seekTo(positionMs: Long) {
    if (isRootPlaybackActive) {
      val clip = activeClipEntity ?: return
      val clamped = positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(0L))
      playClip(clip, startPositionMs = clamped)
      return
    }
    val player = mediaPlayer ?: return
    runCatching {
      val clamped = positionMs.coerceIn(0L, _playbackState.value.durationMs.coerceAtLeast(0L))
      player.seekTo(clamped.toInt())
      _playbackState.value = _playbackState.value.copy(positionMs = clamped)
    }
  }

  fun stop() {
    stopInternal(keepError = false)
  }

  fun setSpeakerphoneEnabled(enabled: Boolean) {
    _playbackState.value = _playbackState.value.copy(speakerphoneEnabled = enabled)
    if (_playbackState.value.isPlaying) {
      if (enabled) {
        applySpeakerRouting(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          findBuiltInSpeakerDevice()?.let { speaker ->
            runCatching { mediaPlayer?.setPreferredDevice(speaker) }
          }
        }
      } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          runCatching { mediaPlayer?.setPreferredDevice(null) }
        }
        restoreSpeakerRouting()
      }
    }
  }

  fun setLoopEnabled(enabled: Boolean) {
    _playbackState.value = _playbackState.value.copy(loopEnabled = enabled)
    runCatching { mediaPlayer?.isLooping = enabled }
    if (isRootPlaybackActive) {
      val clip = activeClipEntity ?: return
      playClip(clip, startPositionMs = _playbackState.value.positionMs)
    }
  }

  fun setVoiceBoostEnabled(enabled: Boolean) {
    _playbackState.value = _playbackState.value.copy(voiceBoostEnabled = enabled)
    if (isRootPlaybackActive) {
      val clip = activeClipEntity ?: return
      playClip(clip, startPositionMs = _playbackState.value.positionMs)
      return
    }
    val player = mediaPlayer
    if (player != null && _playbackState.value.isPlaying) {
      attachOrUpdateLoudnessEnhancer(player.audioSessionId, enabled)
    } else if (!enabled) {
      releaseLoudnessEnhancer()
    }
  }

  fun setAutoUnmuteMicEnabled(enabled: Boolean) {
    _playbackState.value = _playbackState.value.copy(autoUnmuteMicEnabled = enabled)
    if (_playbackState.value.isPlaying) {
      if (enabled) {
        val unmuted = stealthAudioController?.prepareMicForSpeakerPlayback(true) ?: false
        _playbackState.value =
          _playbackState.value.copy(
            micTemporarilyUnmuted = _playbackState.value.micTemporarilyUnmuted || unmuted
          )
      } else {
        stealthAudioController?.restoreMicAfterSpeakerPlayback()
        _playbackState.value = _playbackState.value.copy(micTemporarilyUnmuted = false)
      }
    }
  }

  fun setOutputRoute(route: SpeakerOutputRoute) {
    val previous = _playbackState.value
    if (previous.outputRoute == route) return
    _playbackState.value = previous.copy(outputRoute = route)
    val clip = activeClipEntity
    if (previous.isPlaying && clip != null) {
      val resumeAt =
        runCatching { mediaPlayer?.currentPosition?.toLong() }.getOrNull() ?: previous.positionMs
      playClip(clip, startPositionMs = resumeAt)
    }
  }

  fun release() {
    stopInternal(keepError = false)
    cancelRecordingInternal()
  }

  private fun stopInternal(keepError: Boolean) {
    rootPlayJob?.cancel()
    rootPlayJob = null
    if (isRootPlaybackActive) {
      isRootPlaybackActive = false
      rootAudioBridge.stopRootPlayback()
    }
    progressJob?.cancel()
    progressJob = null
    releaseLoudnessEnhancer()
    runCatching {
      mediaPlayer?.let { player ->
        if (player.isPlaying) player.stop()
        player.reset()
        player.release()
      }
    }
    mediaPlayer = null
    restoreAudioEnvironment()
    _playbackState.value =
      _playbackState.value.copy(
        isPlaying = false,
        isPaused = false,
        positionMs = 0L,
        micTemporarilyUnmuted = false,
        activeRouteDescription = "",
        errorMessage = if (keepError) _playbackState.value.errorMessage else null,
      )
  }

  private fun startProgressTicker() {
    progressJob?.cancel()
    progressJob =
      scope.launch {
        while (isActive) {
          val player = mediaPlayer
          if (player != null) {
            val playing = runCatching { player.isPlaying }.getOrDefault(false)
            if (playing) {
              val pos = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
              val dur =
                runCatching { player.duration.toLong() }
                  .getOrDefault(_playbackState.value.durationMs)
                  .coerceAtLeast(_playbackState.value.durationMs)
              _playbackState.value =
                _playbackState.value.copy(positionMs = pos.coerceAtLeast(0L), durationMs = dur)
            }
          }
          delay(250L)
        }
      }
  }

  private fun buildAudioAttributes(route: SpeakerOutputRoute): AudioAttributes {
    val usage =
      when (route) {
        SpeakerOutputRoute.MEDIA_SPEAKER -> AudioAttributes.USAGE_MEDIA
        SpeakerOutputRoute.VOICE_CALL_SPEAKER,
        SpeakerOutputRoute.ROOT_CALL_INJECT -> AudioAttributes.USAGE_VOICE_COMMUNICATION
      }
    return AudioAttributes.Builder()
      .setUsage(usage)
      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      .build()
  }

  private fun attachOrUpdateLoudnessEnhancer(audioSessionId: Int, boostEnabled: Boolean) {
    releaseLoudnessEnhancer()
    if (!boostEnabled || audioSessionId <= 0) return
    runCatching {
      loudnessEnhancer =
        LoudnessEnhancer(audioSessionId).apply {
          setTargetGain(VOICE_BOOST_TARGET_GAIN_MB)
          enabled = true
        }
    }
  }

  private fun releaseLoudnessEnhancer() {
    runCatching { loudnessEnhancer?.release() }
    loudnessEnhancer = null
  }

  private fun ensureLoudPlaybackVolume(route: SpeakerOutputRoute) {
    val streamType =
      when (route) {
        SpeakerOutputRoute.MEDIA_SPEAKER -> AudioManager.STREAM_MUSIC
        SpeakerOutputRoute.VOICE_CALL_SPEAKER,
        SpeakerOutputRoute.ROOT_CALL_INJECT -> AudioManager.STREAM_VOICE_CALL
      }
    runCatching {
      val currentVol = audioManager.getStreamVolume(streamType)
      val maxVol = audioManager.getStreamMaxVolume(streamType)
      val targetMinVol = ceil(maxVol * 0.90).toInt().coerceIn(1, maxVol)
      if (currentVol < targetMinVol) {
        if (adjustedStreamType == null) {
          adjustedStreamType = streamType
          previousStreamVolume = currentVol
        }
        audioManager.setStreamVolume(streamType, targetMinVol, 0)
      }
    }
  }

  private fun restorePlaybackVolume() {
    val streamType = adjustedStreamType
    val prevVol = previousStreamVolume
    adjustedStreamType = null
    previousStreamVolume = null
    if (streamType != null && prevVol != null) {
      runCatching { audioManager.setStreamVolume(streamType, prevVol, 0) }
    }
  }

  private fun restoreAudioEnvironment() {
    restoreSpeakerRouting()
    restorePlaybackVolume()
    stealthAudioController?.restoreMicAfterSpeakerPlayback()
  }

  private fun findBuiltInSpeakerDevice(): AudioDeviceInfo? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      audioManager.availableCommunicationDevices
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        ?.let { return it }
    }
    return audioManager
      .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
      .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
  }

  @Suppress("DEPRECATION")
  private fun applySpeakerRouting(enableSpeaker: Boolean) {
    if (!enableSpeaker) return
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val speakerDevice = findBuiltInSpeakerDevice()
        if (speakerDevice != null) {
          appliedCommunicationSpeaker = audioManager.setCommunicationDevice(speakerDevice)
        }
      } else {
        if (previousSpeakerphoneState == null) {
          previousSpeakerphoneState = audioManager.isSpeakerphoneOn
        }
        audioManager.isSpeakerphoneOn = true
      }
    }
  }

  @Suppress("DEPRECATION")
  private fun restoreSpeakerRouting() {
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (appliedCommunicationSpeaker) {
          audioManager.clearCommunicationDevice()
          appliedCommunicationSpeaker = false
        }
      } else {
        previousSpeakerphoneState?.let { prev ->
          audioManager.isSpeakerphoneOn = prev
          previousSpeakerphoneState = null
        }
      }
    }
  }

  companion object {
    private const val VOICE_BOOST_TARGET_GAIN_MB = 1000

    suspend fun readPickedAudioMeta(context: Context, uri: Uri): PickedAudioMeta =
      withContext(Dispatchers.IO) {
        var displayName = "Rekaman_Audio"
        var sizeBytes = 0L
        runCatching {
          context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
              if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) {
                  cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                  sizeBytes = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                }
              }
            }
        }
        val cleanSuggested =
          displayName.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "Audio Pembicara" }
        PickedAudioMeta(
          originalFileName = displayName,
          suggestedName = cleanSuggested,
          sizeBytes = sizeBytes,
        )
      }

    suspend fun importAudioFromUri(
      context: Context,
      uri: Uri,
      customName: String,
    ): AudioClipEntity? =
      withContext(Dispatchers.IO) {
        val meta = readPickedAudioMeta(context, uri)
        val mimeType = context.contentResolver.getType(uri) ?: "audio/*"
        val ext =
          meta.originalFileName
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.matches(Regex("^[a-z0-9]{1,6}$")) }
            ?: extensionFromMime(mimeType)

        val dir = File(context.filesDir, "audio_clips").apply { mkdirs() }
        val destFile = File(dir, "${UUID.randomUUID()}.$ext")

        val copied =
          runCatching {
              context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
                true
              } ?: false
            }
            .getOrDefault(false)

        if (!copied || !destFile.exists() || destFile.length() <= 0L) {
          runCatching { destFile.delete() }
          return@withContext null
        }

        val durationMs = probeDurationMs(destFile)
        val finalName = customName.trim().ifBlank { meta.suggestedName }
        AudioClipEntity(
          name = finalName,
          originalFileName = meta.originalFileName,
          filePath = destFile.absolutePath,
          mimeType = mimeType,
          durationMs = durationMs,
          fileSizeBytes = destFile.length(),
        )
      }

    fun finalizeRecordedDraft(draft: RecordedAudioDraft, customName: String): AudioClipEntity {
      val finalName = customName.trim().ifBlank { "Rekaman Suara" }
      return AudioClipEntity(
        name = finalName,
        originalFileName = "Rekaman Suara.m4a",
        filePath = draft.file.absolutePath,
        mimeType = "audio/mp4",
        durationMs = draft.durationMs,
        fileSizeBytes = draft.fileSizeBytes,
      )
    }

    fun discardRecordedDraft(draft: RecordedAudioDraft) {
      runCatching { if (draft.file.exists()) draft.file.delete() }
    }

    fun deleteStoredFile(filePath: String) {
      if (filePath.isBlank()) return
      runCatching {
        val file = File(filePath)
        if (file.exists()) file.delete()
        val wavCache = PcmAudioDecoder.wavCacheFileFor(filePath)
        if (wavCache.exists()) wavCache.delete()
      }
    }

    private fun probeDurationMs(file: File): Long {
      val retriever = MediaMetadataRetriever()
      return try {
        retriever.setDataSource(file.absolutePath)
        retriever
          .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
          ?.toLongOrNull()
          ?.coerceAtLeast(0L) ?: 0L
      } catch (_: Exception) {
        0L
      } finally {
        runCatching { retriever.release() }
      }
    }

    private fun extensionFromMime(mimeType: String): String {
      return when {
        mimeType.contains("mpeg") || mimeType.contains("mp3") -> "mp3"
        mimeType.contains("wav") -> "wav"
        mimeType.contains("ogg") -> "ogg"
        mimeType.contains("opus") -> "opus"
        mimeType.contains("aac") -> "aac"
        mimeType.contains("mp4") || mimeType.contains("m4a") -> "m4a"
        else -> "m4a"
      }
    }

    fun formatDuration(millis: Long): String {
      val totalSecs = (millis.coerceAtLeast(0L) / 1000L).toInt()
      val mins = totalSecs / 60
      val secs = totalSecs % 60
      return "%02d:%02d".format(mins, secs)
    }

    fun formatFileSize(bytes: Long): String {
      if (bytes <= 0L) return "0 KB"
      val kb = bytes / 1024.0
      return if (kb < 1024.0) {
        "${kb.toInt().coerceAtLeast(1)} KB"
      } else {
        String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
      }
    }
  }
}
