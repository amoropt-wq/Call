package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.core.text.HtmlCompat
import com.example.data.prefs.SmtpConfig
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface EmailSendResult {
  data class Success(val message: String) : EmailSendResult

  data class Failure(val error: String) : EmailSendResult
}

object SmtpEmailSender {

  private const val TIMEOUT_MS = 15_000

  suspend fun testConnection(config: SmtpConfig): EmailSendResult =
    withContext(Dispatchers.IO) {
      if (!config.isConfigured) {
        return@withContext EmailSendResult.Failure(
          "Lengkapi host, port, email pengirim, dan password/App Password SMTP terlebih dahulu."
        )
      }
      runCatching {
          openAuthenticatedSession(config) { _, writer, reader ->
            sendLine(writer, "QUIT")
            readMultilineResponse(reader)
          }
          EmailSendResult.Success("Koneksi SMTP ke ${config.host}:${config.port} berhasil terhubung!")
        }
        .getOrElse { e ->
          EmailSendResult.Failure(e.message ?: "Gagal terhubung ke server SMTP.")
        }
    }

  suspend fun sendHtmlEmail(
    config: SmtpConfig,
    toEmail: String,
    subject: String,
    htmlBody: String,
  ): EmailSendResult =
    withContext(Dispatchers.IO) {
      if (!config.isConfigured) {
        return@withContext EmailSendResult.Failure(
          "Koneksi SMTP belum dikonfigurasi di Pengaturan."
        )
      }
      val cleanTo = toEmail.trim()
      if (cleanTo.isBlank() || !cleanTo.contains("@")) {
        return@withContext EmailSendResult.Failure("Alamat email tujuan tidak valid.")
      }

      runCatching {
          openAuthenticatedSession(config) { _, writer, reader ->
            sendCommandAndExpect(writer, reader, "MAIL FROM:<${config.username.trim()}>", 250)
            sendCommandAndExpect(writer, reader, "RCPT TO:<$cleanTo>", 250, 251)
            sendCommandAndExpect(writer, reader, "DATA", 354)

            val encodedSubject = encodeMimeSubject(subject)
            val displayFrom =
              if (config.senderName.isNotBlank()) {
                "${encodeMimeSubject(config.senderName)} <${config.username.trim()}>"
              } else {
                config.username.trim()
              }
            val encodedBody =
              Base64.encodeToString(
                htmlBody.toByteArray(StandardCharsets.UTF_8),
                Base64.CRLF,
              )

            val rfcDate = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date())
            val rawMessage =
              buildString {
                append("Date: ").append(rfcDate).append("\r\n")
                append("From: ").append(displayFrom).append("\r\n")
                append("To: <").append(cleanTo).append(">\r\n")
                append("Subject: ").append(encodedSubject).append("\r\n")
                append("MIME-Version: 1.0\r\n")
                append("Content-Type: text/html; charset=UTF-8\r\n")
                append("Content-Transfer-Encoding: base64\r\n")
                append("\r\n")
                append(encodedBody)
                if (!encodedBody.endsWith("\r\n")) append("\r\n")
                append(".\r\n")
              }

            writer.write(rawMessage)
            writer.flush()
            val dataResp = readMultilineResponse(reader)
            if (dataResp.code != 250) {
              throw IllegalStateException("Server menolak isi email: ${dataResp.text}")
            }

            sendLine(writer, "QUIT")
            readMultilineResponse(reader)
          }
          EmailSendResult.Success("Email HTML berhasil dikirim ke $cleanTo.")
        }
        .getOrElse { e ->
          EmailSendResult.Failure(e.message ?: "Gagal mengirim email via SMTP.")
        }
    }

  fun launchExternalEmailApp(
    context: Context,
    toEmail: String,
    subject: String,
    htmlBody: String,
  ): Boolean {
    val plainPreview =
      HtmlCompat.fromHtml(htmlBody, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
    val intent =
      Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:${Uri.encode(toEmail.trim())}")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail.trim()))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, plainPreview)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    return try {
      context.startActivity(intent)
      true
    } catch (_: Exception) {
      false
    }
  }

  private fun openAuthenticatedSession(
    config: SmtpConfig,
    block: (Socket, BufferedWriter, BufferedReader) -> Unit,
  ) {
    val host = config.host.trim()
    val port = config.port
    var socket: Socket? = null
    try {
      socket =
        if (config.useSsl) {
          val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
          (factory.createSocket() as SSLSocket).apply {
            connect(InetSocketAddress(host, port), TIMEOUT_MS)
            soTimeout = TIMEOUT_MS
            startHandshake()
          }
        } else {
          Socket().apply {
            connect(InetSocketAddress(host, port), TIMEOUT_MS)
            soTimeout = TIMEOUT_MS
          }
        }

      var reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
      var writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))

      val greeting = readMultilineResponse(reader)
      if (greeting.code != 220) {
        throw IllegalStateException("Server tidak siap: ${greeting.text}")
      }

      sendCommandAndExpect(writer, reader, "EHLO haloantri.local", 250)

      if (!config.useSsl) {
        sendCommandAndExpect(writer, reader, "STARTTLS", 220)
        val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val tlsSocket =
          (sslFactory.createSocket(socket, host, port, true) as SSLSocket).apply {
            soTimeout = TIMEOUT_MS
            startHandshake()
          }
        socket = tlsSocket
        reader = BufferedReader(InputStreamReader(tlsSocket.getInputStream(), StandardCharsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(tlsSocket.getOutputStream(), StandardCharsets.UTF_8))
        sendCommandAndExpect(writer, reader, "EHLO haloantri.local", 250)
      }

      // Authenticate using AUTH LOGIN
      sendCommandAndExpect(writer, reader, "AUTH LOGIN", 334)
      val userB64 =
        Base64.encodeToString(
          config.username.trim().toByteArray(StandardCharsets.UTF_8),
          Base64.NO_WRAP,
        )
      sendCommandAndExpect(writer, reader, userB64, 334)
      val passB64 =
        Base64.encodeToString(
          config.password.toByteArray(StandardCharsets.UTF_8),
          Base64.NO_WRAP,
        )
      sendCommandAndExpect(writer, reader, passB64, 235)

      block(socket, writer, reader)
    } finally {
      runCatching { socket?.close() }
    }
  }

  private fun sendLine(writer: BufferedWriter, line: String) {
    writer.write(line)
    writer.write("\r\n")
    writer.flush()
  }

  private fun sendCommandAndExpect(
    writer: BufferedWriter,
    reader: BufferedReader,
    command: String,
    vararg expectedCodes: Int,
  ): SmtpResponse {
    sendLine(writer, command)
    val resp = readMultilineResponse(reader)
    if (resp.code !in expectedCodes) {
      throw IllegalStateException("SMTP Error ${resp.code}: ${resp.text}")
    }
    return resp
  }

  private data class SmtpResponse(val code: Int, val text: String)

  private fun readMultilineResponse(reader: BufferedReader): SmtpResponse {
    val lines = mutableListOf<String>()
    var code = -1
    while (true) {
      val line = reader.readLine() ?: break
      lines.add(line)
      if (line.length >= 3) {
        code = line.substring(0, 3).toIntOrNull() ?: code
      }
      if (line.length < 4 || line[3] == ' ') {
        break
      }
    }
    if (code == -1) {
      throw IllegalStateException("Koneksi terputus oleh server SMTP.")
    }
    return SmtpResponse(code, lines.joinToString(" | "))
  }

  private fun encodeMimeSubject(text: String): String {
    val b64 = Base64.encodeToString(text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    return "=?UTF-8?B?$b64?="
  }
}
