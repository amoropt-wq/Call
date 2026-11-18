package com.example

import com.example.data.local.CallQueueItemEntity
import com.example.domain.EmailTemplateBuilder
import com.example.domain.PhoneNumberExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberExtractorTest {

  @Test
  fun extractsIndonesianAndInternationalNumbersFromMessage() {
    val msg = "Halo kak, tolong hubungi 0812-3456-7890 atau +62 857 1122 3344 ya. ID: CUST-99"
    val extracted = PhoneNumberExtractor.extractPhoneNumbers(msg)
    assertEquals(listOf("+6281234567890", "+6285711223344"), extracted)
    assertEquals("CUST-99", PhoneNumberExtractor.extractIdNumber(msg))
  }

  @Test
  fun rejectsShortInvalidNumbersAndPrices() {
    val msg = "Kode OTP 123456 harga Rp 150.000 tanggal 2026-11-16"
    val extracted = PhoneNumberExtractor.extractPhoneNumbers(msg)
    assertTrue(extracted.isEmpty())
  }

  @Test
  fun formatsDisplayPhoneCorrectly() {
    assertEquals("+62 812-3456-7890", PhoneNumberExtractor.formatDisplayPhone("081234567890"))
  }

  @Test
  fun extractsEmailsFromMessage() {
    val msg = "Tolong hubungi 081234567890 atau email ke cs.budi+vip@contoh.co.id & admin@test.com"
    val emails = PhoneNumberExtractor.extractEmails(msg)
    assertEquals(listOf("cs.budi+vip@contoh.co.id", "admin@test.com"), emails)
  }

  @Test
  fun rendersEmailTemplatePlaceholdersAndEscapesHtml() {
    val template = EmailTemplateBuilder.defaultTemplate()
    val contact =
      CallQueueItemEntity(
        name = "Andi <VIP>",
        phoneNumber = "+6281234567890",
        idNumber = "ID-77",
        email = "andi@contoh.com",
        notes = "Minta penawaran resmi",
      )

    val subject = EmailTemplateBuilder.renderSubject(template, contact)
    val html = EmailTemplateBuilder.buildRenderedHtml(template, contact)

    assertEquals("Informasi Layanan untuk Andi <VIP> (ID-77)", subject)
    assertTrue(html.contains("Andi &lt;VIP&gt;"))
    assertFalse(html.contains("Andi <VIP>"))
    assertTrue(html.contains("+62 812-3456-7890"))
    assertTrue(html.contains("ID-77"))
    assertTrue(html.contains("andi@contoh.com"))
    assertTrue(html.contains("Minta penawaran resmi"))
  }

  @Test
  fun rendersHeaderAndFooterLogosAndSanitizesUnsafeUrls() {
    val template =
      EmailTemplateBuilder.defaultTemplate()
        .copy(
          headerLogoUrl = "https://example.com/header.png",
          footerLogoUrl = "data:image/png;base64,AAAA",
        )
    val html = EmailTemplateBuilder.buildRenderedHtml(template, null)
    assertTrue(html.contains("https://example.com/header.png"))
    assertTrue(html.contains("data:image/png;base64,AAAA"))
    assertTrue(html.contains("border:none; outline:none; background:transparent; padding:0;"))
    assertFalse(html.contains("rgba(255,255,255,0.12)"))

    val centeredHtml =
      EmailTemplateBuilder.buildRenderedHtml(
        template.copy(headerStyle = com.example.domain.EmailHeaderStyle.CENTERED_BADGE.key),
        null,
      )
    assertTrue(centeredHtml.contains("text-align:center;"))

    val unsafeTemplate =
      template.copy(
        headerLogoUrl = "javascript:alert(1)",
        footerLogoUrl = "file:///etc/passwd",
      )
    val safeHtml = EmailTemplateBuilder.buildRenderedHtml(unsafeTemplate, null)
    assertFalse(safeHtml.contains("javascript:"))
    assertFalse(safeHtml.contains("file:///"))
  }

  @Test
  fun stampsAutomaticSendDateAndTimeInRenderedEmail() {
    val fixedMillis = 1794950400000L // 2026-11-17
    val template =
      EmailTemplateBuilder.defaultTemplate()
        .copy(
          subject = "Update {{tanggal}}",
          bodyHtml = "<p>Dikirim {{tanggal}} jam {{waktu}} ({{tanggal_waktu}})</p>",
        )
    val expectedDate = EmailTemplateBuilder.formatDate(fixedMillis)
    val expectedTime = EmailTemplateBuilder.formatTime(fixedMillis)
    val expectedDateTime = EmailTemplateBuilder.formatDateTime(fixedMillis)

    val subject = EmailTemplateBuilder.renderSubject(template, null, fixedMillis)
    val html = EmailTemplateBuilder.buildRenderedHtml(template, null, fixedMillis)

    assertEquals("Update $expectedDate", subject)
    assertTrue(html.contains(expectedDate))
    assertTrue(html.contains(expectedTime))
    assertTrue(html.contains("Dikirim pada: $expectedDateTime"))
  }

  @Test
  fun formatsSpeakerAudioDurationAndFileSize() {
    assertEquals("00:00", com.example.service.SpeakerAudioPlayerController.formatDuration(0L))
    assertEquals("01:05", com.example.service.SpeakerAudioPlayerController.formatDuration(65_000L))
    assertEquals("1 KB", com.example.service.SpeakerAudioPlayerController.formatFileSize(500L))
    assertEquals("1.5 MB", com.example.service.SpeakerAudioPlayerController.formatFileSize(1_572_864L))

    val clip =
      com.example.data.local.AudioClipEntity(
        name = "Salam Pembuka Pelanggan",
        originalFileName = "recording_01.mp3",
        filePath = "/tmp/test.mp3",
        durationMs = 12_000L,
        fileSizeBytes = 2048L,
      )
    assertEquals("Salam Pembuka Pelanggan", clip.name)
    assertEquals(12_000L, clip.durationMs)

    val defaultPlayback = com.example.service.AudioPlaybackState()
    assertTrue(defaultPlayback.speakerphoneEnabled)
    assertTrue(defaultPlayback.voiceBoostEnabled)
    assertTrue(defaultPlayback.autoUnmuteMicEnabled)
    assertEquals(
      com.example.service.SpeakerOutputRoute.MEDIA_SPEAKER,
      defaultPlayback.outputRoute,
    )
  }

  @Test
  fun extractsStructuredContactAndNeverCallsSenderPhoneNumber() {
    val msg =
      """
      Nama PURWANTO
      Nomor hp 08995385444
      Nomor kartu 4799 3111 0126 8348
      Email/yaho mfajar444@gmail.com
      """.trimIndent()

    // Case 1: Sender is "AGUNG"
    val parsedFromAgung = PhoneNumberExtractor.extractStructuredContact("AGUNG", msg)
    assertEquals("AGUNG", parsedFromAgung.senderName)
    assertEquals("PURWANTO", parsedFromAgung.contactName)
    assertEquals(listOf("+628995385444"), parsedFromAgung.phoneNumbers)
    assertEquals("4799 3111 0126 8348", parsedFromAgung.idOrCardNumber)
    assertEquals("mfajar444@gmail.com", parsedFromAgung.email)

    // Case 2: Sender is an unsaved phone number -> must remain sender, NOT queued for calling
    val parsedFromPhoneSender =
      PhoneNumberExtractor.extractStructuredContact("+62 811-2233-4455", msg)
    assertEquals("+62 811-2233-4455", parsedFromPhoneSender.senderName)
    assertEquals("PURWANTO", parsedFromPhoneSender.contactName)
    assertEquals(listOf("+628995385444"), parsedFromPhoneSender.phoneNumbers)
    assertFalse(parsedFromPhoneSender.phoneNumbers.contains("+6281122334455"))

    // Case 3: Inline "AGUNG Nama PURWANTO" with blank senderTitle
    val inlineMsg =
      """
      AGUNG Nama PURWANTO
      Nomor hp 08995385444
      Nomor kartu 4799 0812 3456 7890
      Email/yaho mfajar444@gmail.com
      """.trimIndent()
    val parsedInline = PhoneNumberExtractor.extractStructuredContact("", inlineMsg)
    assertEquals("AGUNG", parsedInline.senderName)
    assertEquals("PURWANTO", parsedInline.contactName)
    assertEquals("4799 0812 3456 7890", parsedInline.idOrCardNumber)
    // Card number containing 0812 must NOT be extracted as a callable phone number
    assertEquals(listOf("+628995385444"), parsedInline.phoneNumbers)
  }

  @Test
  fun buildsCustomManualEmailHtmlWithAndWithoutTemplateWrapper() {
    val template =
      EmailTemplateBuilder.defaultTemplate()
        .copy(
          headerLogoUrl = "https://example.com/logo.png",
          footerText = "Tim Layanan HaloAntri",
        )
    val recipient =
      CallQueueItemEntity(
        name = "PURWANTO",
        phoneNumber = "",
        idNumber = "4799 3111 0126 8348",
        email = "mfajar444@gmail.com",
      )
    val fixedMillis = 1794950400000L

    val wrappedHtml =
      EmailTemplateBuilder.buildCustomManualEmailHtml(
        bodyText = "Halo {{nama}},\nData kartu {{nomor_id}} & HP {{nomor_hp}} telah dicek <VIP>.",
        template = template,
        contact = recipient,
        wrapWithTemplateDesign = true,
        sentAtMillis = fixedMillis,
      )
    assertTrue(wrappedHtml.contains("https://example.com/logo.png"))
    assertTrue(wrappedHtml.contains("Halo PURWANTO,"))
    assertTrue(wrappedHtml.contains("4799 3111 0126 8348"))
    assertTrue(wrappedHtml.contains("HP -"))
    assertTrue(wrappedHtml.contains("&lt;VIP&gt;"))
    assertTrue(wrappedHtml.contains("Tim Layanan HaloAntri"))

    val plainHtml =
      EmailTemplateBuilder.buildCustomManualEmailHtml(
        bodyText = "Pesan singkat manual untuk {{nama}}.",
        template = template,
        contact = recipient,
        wrapWithTemplateDesign = false,
        sentAtMillis = fixedMillis,
      )
    assertTrue(plainHtml.contains("Pesan singkat manual untuk PURWANTO."))
    assertFalse(plainHtml.contains("https://example.com/logo.png"))
  }

  @Test
  fun validatesRootAudioRouteDiagnosticsAndWavCacheParsing() {
    val rootRoute = com.example.service.SpeakerOutputRoute.ROOT_CALL_INJECT
    assertEquals("Injeksi Root (su)", rootRoute.label)

    val defaultDiag = com.example.service.RootAudioDiagnostics()
    assertFalse(defaultDiag.isChecked)
    assertFalse(defaultDiag.isRootGranted)
    assertFalse(defaultDiag.hasTinyPlay)
    assertFalse(defaultDiag.hasTinyMix)

    val playbackState = com.example.service.AudioPlaybackState()
    assertEquals(defaultDiag, playbackState.rootDiagnostics)
    assertEquals("", playbackState.activeRouteDescription)

    val cacheFile = com.example.service.PcmAudioDecoder.wavCacheFileFor("/tmp/salam_pelanggan.m4a")
    assertEquals("/tmp/salam_pelanggan.m4a.root.wav", cacheFile.path)

    // Verify RIFF WAV header parsing against a temp file (16-bit mono 16kHz, 1 second = 32000 PCM bytes)
    val tempWav = java.io.File.createTempFile("haloantri_test_", ".wav")
    try {
      val pcmBytes = 32_000
      val header =
        java.nio.ByteBuffer.allocate(44 + pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
          put("RIFF".toByteArray(Charsets.US_ASCII))
          putInt(36 + pcmBytes)
          put("WAVE".toByteArray(Charsets.US_ASCII))
          put("fmt ".toByteArray(Charsets.US_ASCII))
          putInt(16)
          putShort(1.toShort())
          putShort(1.toShort()) // mono
          putInt(16_000) // sampleRate
          putInt(32_000) // byteRate
          putShort(2.toShort()) // blockAlign
          putShort(16.toShort()) // bitsPerSample
          put("data".toByteArray(Charsets.US_ASCII))
          putInt(pcmBytes)
        }
      tempWav.writeBytes(header.array())

      val info = com.example.service.PcmAudioDecoder.readWavHeader(tempWav)
      assertTrue(info != null)
      assertEquals(16_000, info!!.sampleRate)
      assertEquals(1, info.channelCount)
      assertEquals(1_000L, info.durationMs)
    } finally {
      tempWav.delete()
    }
  }
}
