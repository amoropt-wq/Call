package com.example.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Looper
import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Standalone entry point executed via `su` + `app_process` so AudioTrack runs with uid=0 (root),
 * enabling privileged call-injection AudioAttributes (USAGE_CALL_ASSISTANT on Android 13+ or
 * privileged USAGE_VOICE_COMMUNICATION) and LoudnessEnhancer directly into active VoIP/call mixes.
 *
 * Arguments: <wavPath> <loopBoolean> <gainMb> <startPosMs>
 */
object RootAudioProcess {

  private const val USAGE_CALL_ASSISTANT = 17

  @JvmStatic
  fun main(args: Array<String>) {
    if (Looper.getMainLooper() == null) {
      @Suppress("DEPRECATION")
      Looper.prepareMainLooper()
    }

    if (args.isEmpty()) {
      println("ERROR:Argumen file audio tidak ditemukan")
      return
    }

    val wavPath = args[0]
    val loopEnabled = args.getOrNull(1)?.toBooleanStrictOrNull() ?: false
    val gainMb = args.getOrNull(2)?.toIntOrNull() ?: 1000
    val startPosMs = args.getOrNull(3)?.toLongOrNull() ?: 0L

    val wavFile = File(wavPath)
    val wavInfo = PcmAudioDecoder.readWavHeader(wavFile)
    if (wavInfo == null) {
      println("ERROR:File PCM WAV tidak valid")
      return
    }

    val stopFlag = AtomicBoolean(false)
    val stdinWatcher =
      Thread {
        runCatching {
          val reader = BufferedReader(InputStreamReader(System.`in`))
          while (!stopFlag.get()) {
            val line = reader.readLine() ?: break
            if (line.trim().equals("STOP", ignoreCase = true)) {
              stopFlag.set(true)
              break
            }
          }
        }
      }.apply {
        isDaemon = true
        start()
      }

    runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }

    val channelMask =
      if (wavInfo.channelCount >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
    val minBuf =
      AudioTrack.getMinBufferSize(
        wavInfo.sampleRate,
        channelMask,
        AudioFormat.ENCODING_PCM_16BIT,
      ).coerceAtLeast(4096)
    val bufferSize = (minBuf * 2).coerceAtLeast(8192)

    val audioFormat =
      AudioFormat.Builder()
        .setSampleRate(wavInfo.sampleRate)
        .setChannelMask(channelMask)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()

    val (track, routeDesc) = createPrivilegedAudioTrack(audioFormat, bufferSize)
    if (track == null) {
      println("ERROR:Gagal membuat AudioTrack root")
      return
    }

    var enhancer: LoudnessEnhancer? = null
    if (gainMb > 0) {
      runCatching {
        enhancer =
          LoudnessEnhancer(track.audioSessionId).apply {
            setTargetGain(gainMb)
            enabled = true
          }
      }
    }

    println("STARTED:$routeDesc:${wavInfo.durationMs}")
    System.out.flush()

    val bytesPerSec = (wavInfo.sampleRate * wavInfo.channelCount * 2).coerceAtLeast(1)
    var currentStartByte = ((startPosMs.coerceAtLeast(0L) * bytesPerSec) / 1000L).let { it - (it % 4L) }

    try {
      track.setVolume(1.0f)
      track.play()

      val chunk = ByteArray(4096)
      var lastReportMs = 0L

      do {
        FileInputStream(wavFile).use { fis ->
          val headerSkip = 44L + currentStartByte.coerceAtLeast(0L)
          var skipped = 0L
          while (skipped < headerSkip) {
            val s = fis.skip(headerSkip - skipped)
            if (s <= 0L) break
            skipped += s
          }
          var bytesPlayed = currentStartByte.coerceAtLeast(0L)
          currentStartByte = 0L

          while (!stopFlag.get()) {
            val read = fis.read(chunk)
            if (read <= 0) break
            var offset = 0
            while (offset < read && !stopFlag.get()) {
              val written =
                track.write(chunk, offset, read - offset, AudioTrack.WRITE_BLOCKING)
              if (written <= 0) break
              offset += written
              bytesPlayed += written.toLong()
            }
            val now = System.currentTimeMillis()
            if (now - lastReportMs >= 250L) {
              lastReportMs = now
              val posMs = (bytesPlayed * 1000L) / bytesPerSec
              println("POS:$posMs:${wavInfo.durationMs}")
              System.out.flush()
            }
          }
        }
      } while (loopEnabled && !stopFlag.get())

      if (!stopFlag.get()) {
        println("COMPLETED")
        System.out.flush()
      }
    } catch (e: Exception) {
      println("ERROR:${e.message ?: "Error pemutaran root"}")
      System.out.flush()
    } finally {
      stopFlag.set(true)
      stdinWatcher.interrupt()
      runCatching { enhancer?.release() }
      runCatching {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
          track.pause()
          track.flush()
        }
        track.stop()
      }
      runCatching { track.release() }
    }
  }

  private fun createPrivilegedAudioTrack(
    audioFormat: AudioFormat,
    bufferSize: Int,
  ): Pair<AudioTrack?, String> {
    // 1. Android 13+ (API 33+): Try USAGE_CALL_ASSISTANT (17) via SystemApi builder method as uid=0
    if (Build.VERSION.SDK_INT >= 33) {
      buildTrackWithUsage(
        audioFormat = audioFormat,
        bufferSize = bufferSize,
        usage = USAGE_CALL_ASSISTANT,
        useSystemApiSetter = true,
      )?.let { return it to "Root Call-Assistant (uid=0)" }
    }

    // 2. Privileged Voice Communication usage as uid=0
    buildTrackWithUsage(
      audioFormat = audioFormat,
      bufferSize = bufferSize,
      usage = AudioAttributes.USAGE_VOICE_COMMUNICATION,
      useSystemApiSetter = false,
    )?.let { return it to "Root Voice-Comm (uid=0)" }

    // 3. Fallback Media Speech usage as uid=0
    buildTrackWithUsage(
      audioFormat = audioFormat,
      bufferSize = bufferSize,
      usage = AudioAttributes.USAGE_MEDIA,
      useSystemApiSetter = false,
    )?.let { return it to "Root Media-Speaker (uid=0)" }

    return null to ""
  }

  private fun buildTrackWithUsage(
    audioFormat: AudioFormat,
    bufferSize: Int,
    usage: Int,
    useSystemApiSetter: Boolean,
  ): AudioTrack? {
    return runCatching {
      val attrBuilder =
        AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
      if (useSystemApiSetter) {
        val method =
          AudioAttributes.Builder::class.java.getMethod(
            "setSystemApiUsage",
            Int::class.javaPrimitiveType,
          )
        method.invoke(attrBuilder, usage)
      } else {
        attrBuilder.setUsage(usage)
      }
      val track =
        AudioTrack.Builder()
          .setAudioAttributes(attrBuilder.build())
          .setAudioFormat(audioFormat)
          .setBufferSizeInBytes(bufferSize)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()
      if (track.state == AudioTrack.STATE_INITIALIZED) {
        track
      } else {
        track.release()
        null
      }
    }.getOrNull()
  }
}
