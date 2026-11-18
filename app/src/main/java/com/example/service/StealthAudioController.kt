package com.example.service

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StealthAudioController(context: Context) {

  private val audioManager =
    context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private val _isMicMuted = MutableStateFlow(readHardwareMicMuted())
  val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

  private val _audioModeLabel = MutableStateFlow(readAudioModeLabel())
  val audioModeLabel: StateFlow<String> = _audioModeLabel.asStateFlow()

  private var mutedBeforePlayback: Boolean = false

  fun setStealthMute(muted: Boolean): Boolean {
    mutedBeforePlayback = false
    return applyHardwareMute(muted)
  }

  private fun applyHardwareMute(muted: Boolean): Boolean {
    return try {
      audioManager.isMicrophoneMute = muted
      val actual = audioManager.isMicrophoneMute
      _isMicMuted.value = actual
      _audioModeLabel.value = readAudioModeLabel()
      actual
    } catch (_: SecurityException) {
      _isMicMuted.value = readHardwareMicMuted()
      _isMicMuted.value
    }
  }

  fun prepareMicForSpeakerPlayback(autoUnmute: Boolean): Boolean {
    if (!autoUnmute) return false
    val currentlyMuted = readHardwareMicMuted()
    if (currentlyMuted) {
      mutedBeforePlayback = true
      applyHardwareMute(false)
      return true
    }
    return false
  }

  fun restoreMicAfterSpeakerPlayback() {
    if (mutedBeforePlayback) {
      mutedBeforePlayback = false
      applyHardwareMute(true)
    }
  }

  fun toggleStealthMute(): Boolean {
    val target = !readHardwareMicMuted()
    return setStealthMute(target)
  }

  fun syncHardwareState() {
    _isMicMuted.value = readHardwareMicMuted()
    _audioModeLabel.value = readAudioModeLabel()
  }

  private fun readHardwareMicMuted(): Boolean {
    return runCatching { audioManager.isMicrophoneMute }.getOrDefault(false)
  }

  private fun readAudioModeLabel(): String {
    return when (runCatching { audioManager.mode }.getOrDefault(AudioManager.MODE_NORMAL)) {
      AudioManager.MODE_IN_COMMUNICATION -> "VoIP / Komunikasi"
      AudioManager.MODE_IN_CALL -> "Panggilan Seluler"
      AudioManager.MODE_RINGTONE -> "Nada Dering"
      else -> "Siaga Normal"
    }
  }
}
