package com.example.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DecodedWavInfo(
  val wavFile: File,
  val sampleRate: Int,
  val channelCount: Int,
  val durationMs: Long,
)

object PcmAudioDecoder {

  fun wavCacheFileFor(sourcePath: String): File = File("$sourcePath.root.wav")

  suspend fun decodeToWav(sourceFile: File): DecodedWavInfo? =
    withContext(Dispatchers.IO) {
      if (!sourceFile.exists() || sourceFile.length() <= 0L) return@withContext null

      val cachedWav = wavCacheFileFor(sourceFile.absolutePath)
      if (cachedWav.exists() && cachedWav.length() > 44L && cachedWav.lastModified() >= sourceFile.lastModified()) {
        readWavHeader(cachedWav)?.let { return@withContext it }
      }

      val tempWav = File(sourceFile.parentFile ?: sourceFile.absoluteFile.parentFile, "${sourceFile.name}.tmp.wav")
      runCatching { if (tempWav.exists()) tempWav.delete() }

      val extractor = MediaExtractor()
      var codec: MediaCodec? = null
      var raf: RandomAccessFile? = null

      try {
        extractor.setDataSource(sourceFile.absolutePath)
        var audioTrackIndex = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
          val format = extractor.getTrackFormat(i)
          val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
          if (mime.startsWith("audio/")) {
            audioTrackIndex = i
            inputFormat = format
            break
          }
        }
        if (audioTrackIndex < 0 || inputFormat == null) return@withContext null

        extractor.selectTrack(audioTrackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
        var sampleRate =
          if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
          } else {
            44_100
          }
        var channelCount =
          if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
          } else {
            1
          }

        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        raf = RandomAccessFile(tempWav, "rw")
        raf.setLength(0L)
        // Reserve 44 bytes for RIFF WAV header
        raf.write(ByteArray(44))

        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        var totalPcmBytes = 0L

        while (!sawOutputEos) {
          if (!sawInputEos) {
            val inputIndex = codec.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
              val inputBuffer = codec.getInputBuffer(inputIndex)
              if (inputBuffer != null) {
                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) {
                  codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    0L,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                  )
                  sawInputEos = true
                } else {
                  val presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                  codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                  extractor.advance()
                }
              }
            }
          }

          val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
          when {
            outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              val outFormat = codec.outputFormat
              if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
              }
              if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channelCount = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
              }
            }
            outputIndex >= 0 -> {
              val outBuf: ByteBuffer? = codec.getOutputBuffer(outputIndex)
              if (outBuf != null && bufferInfo.size > 0) {
                outBuf.position(bufferInfo.offset)
                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                val chunk = ByteArray(bufferInfo.size)
                outBuf.get(chunk)
                raf.write(chunk)
                totalPcmBytes += chunk.size.toLong()
              }
              codec.releaseOutputBuffer(outputIndex, false)
              if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                sawOutputEos = true
              }
            }
          }
        }

        if (totalPcmBytes <= 0L) {
          raf.close()
          raf = null
          runCatching { tempWav.delete() }
          return@withContext null
        }

        writeWavHeader(raf, totalPcmBytes, sampleRate, channelCount, bitsPerSample = 16)
        raf.close()
        raf = null

        runCatching { if (cachedWav.exists()) cachedWav.delete() }
        if (!tempWav.renameTo(cachedWav)) {
          tempWav.copyTo(cachedWav, overwrite = true)
          runCatching { tempWav.delete() }
        }

        val bytesPerSec = (sampleRate * channelCount * 2).coerceAtLeast(1)
        val durationMs = (totalPcmBytes * 1000L) / bytesPerSec
        DecodedWavInfo(
          wavFile = cachedWav,
          sampleRate = sampleRate,
          channelCount = channelCount,
          durationMs = durationMs,
        )
      } catch (_: Exception) {
        runCatching { tempWav.delete() }
        null
      } finally {
        runCatching { raf?.close() }
        runCatching {
          codec?.stop()
          codec?.release()
        }
        runCatching { extractor.release() }
      }
    }

  private fun writeWavHeader(
    raf: RandomAccessFile,
    pcmDataBytes: Long,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
  ) {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt((36L + pcmDataBytes).toInt())
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // PCM fmt chunk size
    header.putShort(1.toShort()) // AudioFormat = 1 (PCM)
    header.putShort(channels.toShort())
    header.putInt(sampleRate)
    header.putInt(byteRate)
    header.putShort(blockAlign.toShort())
    header.putShort(bitsPerSample.toShort())
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(pcmDataBytes.toInt())

    raf.seek(0L)
    raf.write(header.array())
  }

  fun readWavHeader(wavFile: File): DecodedWavInfo? {
    if (!wavFile.exists() || wavFile.length() <= 44L) return null
    return runCatching {
      RandomAccessFile(wavFile, "r").use { raf ->
        val header = ByteArray(44)
        raf.readFully(header)
        val riff = String(header, 0, 4, Charsets.US_ASCII)
        val wave = String(header, 8, 4, Charsets.US_ASCII)
        if (riff != "RIFF" || wave != "WAVE") return null
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val channels = buf.getShort(22).toInt().coerceIn(1, 2)
        val sampleRate = buf.getInt(24).coerceAtLeast(8_000)
        val dataBytes = (wavFile.length() - 44L).coerceAtLeast(0L)
        val bytesPerSec = (sampleRate * channels * 2).coerceAtLeast(1)
        val durationMs = (dataBytes * 1000L) / bytesPerSec
        DecodedWavInfo(
          wavFile = wavFile,
          sampleRate = sampleRate,
          channelCount = channels,
          durationMs = durationMs,
        )
      }
    }.getOrNull()
  }
}
