package com.example.service

import android.content.Context
import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RootAudioDiagnostics(
  val isChecked: Boolean = false,
  val isRootGranted: Boolean = false,
  val hasTinyPlay: Boolean = false,
  val hasTinyMix: Boolean = false,
  val appProcessPath: String = "",
  val summary: String = "Belum diperiksa — ketuk Cek Root (su)",
)

class RootAudioBridge(
  context: Context,
  private val scope: CoroutineScope,
) {
  private val appContext = context.applicationContext

  private var activeProcess: Process? = null
  private var stdinWriter: OutputStreamWriter? = null
  private var readerJob: Job? = null

  suspend fun probeRootDiagnostics(): RootAudioDiagnostics =
    withContext(Dispatchers.IO) {
      val suBinary = findSuBinary()
      if (suBinary == null) {
        return@withContext RootAudioDiagnostics(
          isChecked = true,
          isRootGranted = false,
          summary = "Binary 'su' tidak ditemukan di perangkat ini.",
        )
      }

      val probeCmd =
        """
        id
        which tinyplay 2>/dev/null || true
        which tinymix 2>/dev/null || true
        ls /system/bin/app_process64 /system/bin/app_process32 /system/bin/app_process 2>/dev/null | head -n 1
        """.trimIndent()

      val result = runSuCommand(suBinary, probeCmd, timeoutSec = 8L)
      val granted = result.output.contains("uid=0")
      if (!granted) {
        return@withContext RootAudioDiagnostics(
          isChecked = true,
          isRootGranted = false,
          summary =
            "Akses root (su) ditolak atau belum diizinkan di Magisk/Superuser.",
        )
      }

      val lines = result.output.lines().map { it.trim() }
      val hasTinyPlay = lines.any { it.endsWith("/tinyplay") }
      val hasTinyMix = lines.any { it.endsWith("/tinymix") }
      val appProc =
        lines.firstOrNull { it.contains("app_process") } ?: defaultAppProcessPath()

      val tools =
        buildList {
          add("uid=0")
          add(if (Build.VERSION.SDK_INT >= 33) "Call-Assistant" else "Voice-Comm")
          if (hasTinyPlay && hasTinyMix) add("ALSA tinyplay/tinymix")
        }.joinToString(" • ")

      RootAudioDiagnostics(
        isChecked = true,
        isRootGranted = true,
        hasTinyPlay = hasTinyPlay,
        hasTinyMix = hasTinyMix,
        appProcessPath = appProc,
        summary = "Root aktif ($tools)",
      )
    }

  suspend fun startRootPlayback(
    wavFile: File,
    loopEnabled: Boolean,
    gainMb: Int,
    startPositionMs: Long,
    onStarted: (routeDescription: String, totalDurationMs: Long) -> Unit,
    onProgress: (positionMs: Long, totalDurationMs: Long) -> Unit,
    onCompleted: () -> Unit,
    onError: (String) -> Unit,
  ): Boolean {
    stopRootPlayback()

    val suBinary = findSuBinary()
    if (suBinary == null) {
      onError("Binary su tidak ditemukan.")
      return false
    }

    val apkPath = appContext.applicationInfo.sourceDir
    val appProcessBin =
      withContext(Dispatchers.IO) {
        listOf("/system/bin/app_process64", "/system/bin/app_process32", "/system/bin/app_process")
          .firstOrNull { File(it).exists() } ?: defaultAppProcessPath()
      }

    // Ensure microphone is unmuted & communication stream is unblocked via root before playing
    val shellScript =
      """
      export CLASSPATH="$apkPath"
      exec $appProcessBin /system/bin com.example.service.RootAudioProcess "${wavFile.absolutePath}" "$loopEnabled" "$gainMb" "$startPositionMs"
      """.trimIndent()

    return withContext(Dispatchers.IO) {
      try {
        val process = ProcessBuilder(suBinary, "-c", shellScript).redirectErrorStream(true).start()
        activeProcess = process
        stdinWriter = OutputStreamWriter(process.outputStream)

        var startedSignaled = false
        readerJob =
          scope.launch(Dispatchers.IO) {
            try {
              val reader = BufferedReader(InputStreamReader(process.inputStream))
              while (true) {
                val line = reader.readLine() ?: break
                when {
                  line.startsWith("STARTED:") -> {
                    val parts = line.removePrefix("STARTED:").split(":")
                    val desc = parts.getOrNull(0).orEmpty().ifBlank { "Root Audio (uid=0)" }
                    val dur = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    startedSignaled = true
                    withContext(Dispatchers.Main) { onStarted(desc, dur) }
                  }
                  line.startsWith("POS:") -> {
                    val parts = line.removePrefix("POS:").split(":")
                    val pos = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                    val dur = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    withContext(Dispatchers.Main) { onProgress(pos, dur) }
                  }
                  line == "COMPLETED" -> {
                    withContext(Dispatchers.Main) { onCompleted() }
                  }
                  line.startsWith("ERROR:") -> {
                    val msg = line.removePrefix("ERROR:").trim()
                    withContext(Dispatchers.Main) { onError(msg) }
                  }
                }
              }
              val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
              if (!startedSignaled && activeProcess === process) {
                withContext(Dispatchers.Main) {
                  onError("Proses root audio berhenti (kode $exitCode). Pastikan izin root diberikan.")
                }
              }
            } catch (_: Exception) {
              // Terminated on stop
            }
          }
        true
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          onError(e.message ?: "Gagal menjalankan proses injeksi audio root.")
        }
        false
      }
    }
  }

  fun stopRootPlayback() {
    val proc = activeProcess
    val writer = stdinWriter
    activeProcess = null
    stdinWriter = null
    readerJob?.cancel()
    readerJob = null

    if (writer != null) {
      runCatching {
        writer.write("STOP\n")
        writer.flush()
        writer.close()
      }
    }
    if (proc != null) {
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          if (!proc.waitFor(350L, TimeUnit.MILLISECONDS)) {
            proc.destroyForcibly()
          }
        } else {
          proc.destroy()
        }
      }
    }
  }

  private fun findSuBinary(): String? {
    val candidates =
      listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/magisk/.core/bin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
      )
    candidates.firstOrNull { File(it).exists() }?.let { return it }
    val pathCheck =
      runCatching {
        val p = ProcessBuilder("sh", "-c", "which su").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out.takeIf { it.isNotBlank() && File(it).exists() }
      }.getOrNull()
    return pathCheck
  }

  private fun defaultAppProcessPath(): String {
    return if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
      "/system/bin/app_process64"
    } else {
      "/system/bin/app_process"
    }
  }

  private data class SuCommandResult(val exitCode: Int, val output: String)

  private fun runSuCommand(suBinary: String, command: String, timeoutSec: Long): SuCommandResult {
    return try {
      val process = ProcessBuilder(suBinary, "-c", command).redirectErrorStream(true).start()
      val completed =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          process.waitFor(timeoutSec, TimeUnit.SECONDS)
        } else {
          process.waitFor()
          true
        }
      if (!completed) {
        process.destroy()
        return SuCommandResult(-1, "")
      }
      val output = process.inputStream.bufferedReader().readText()
      SuCommandResult(process.exitValue(), output)
    } catch (_: Exception) {
      SuCommandResult(-1, "")
    }
  }
}
