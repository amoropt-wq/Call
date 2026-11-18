package com.example.domain

import com.example.data.local.CallQueueItemEntity
import com.example.data.local.EmailTemplateEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EmailHeaderStyle(
  val key: String,
  val label: String,
) {
  SOLID_LEFT("SOLID_LEFT", "Banner Solid"),
  CENTERED_BADGE("CENTERED_BADGE", "Tengah Elegan"),
  GRADIENT_MODERN("GRADIENT_MODERN", "Gradasi Modern"),
  SPLIT_SIDE("SPLIT_SIDE", "Logo & Judul Sejajar"),
  MINIMAL_WHITE("MINIMAL_WHITE", "Minimalis Bersih"),
  SOFT_TINT("SOFT_TINT", "Tint Lembut");

  companion object {
    fun fromKey(key: String): EmailHeaderStyle =
      entries.firstOrNull { it.key == key } ?: SOLID_LEFT
  }
}

enum class EmailVisualTheme(
  val key: String,
  val label: String,
  val outerBg: String,
  val cardBg: String,
  val textColor: String,
  val mutedTextColor: String,
) {
  CLEAN_LIGHT(
    key = "CLEAN_LIGHT",
    label = "Putih Bersih",
    outerBg = "#F4F7F6",
    cardBg = "#FFFFFF",
    textColor = "#111B21",
    mutedTextColor = "#54656F",
  ),
  SOFT_MINT(
    key = "SOFT_MINT",
    label = "Mint Lembut",
    outerBg = "#E6F4EF",
    cardBg = "#FFFFFF",
    textColor = "#0F241D",
    mutedTextColor = "#426357",
  ),
  SKY_BREEZE(
    key = "SKY_BREEZE",
    label = "Biru Langit",
    outerBg = "#EBF5FF",
    cardBg = "#FFFFFF",
    textColor = "#0F172A",
    mutedTextColor = "#475569",
  ),
  WARM_CREAM(
    key = "WARM_CREAM",
    label = "Krem Hangat",
    outerBg = "#FDF6EC",
    cardBg = "#FFFFFF",
    textColor = "#292018",
    mutedTextColor = "#6E5D4F",
  ),
  LAVENDER_SOFT(
    key = "LAVENDER_SOFT",
    label = "Lavender",
    outerBg = "#F3EFFE",
    cardBg = "#FFFFFF",
    textColor = "#1E1633",
    mutedTextColor = "#5B5175",
  ),
  DARK_SLATE(
    key = "DARK_SLATE",
    label = "Gelap Elegan",
    outerBg = "#0B141A",
    cardBg = "#111B21",
    textColor = "#E9EDEF",
    mutedTextColor = "#8696A0",
  );

  companion object {
    fun fromKey(key: String): EmailVisualTheme =
      entries.firstOrNull { it.key == key } ?: CLEAN_LIGHT
  }
}

object EmailTemplateBuilder {

  val ACCENT_COLOR_PRESETS =
    listOf(
      "#007A5E" to "Emerald",
      "#0D6E8A" to "Teal",
      "#0284C7" to "Sky",
      "#1D4ED8" to "Royal",
      "#4338CA" to "Indigo",
      "#7C3AED" to "Violet",
      "#BE123C" to "Rose",
      "#DC2626" to "Crimson",
      "#EA580C" to "Orange",
      "#D97706" to "Amber",
      "#0F766E" to "Pine",
      "#1E293B" to "Slate",
    )

  val HEADER_TITLE_PRESETS =
    listOf(
      "HaloAntri • Tindak Lanjut Panggilan",
      "Konfirmasi Antrian Layanan",
      "Informasi Pelanggan & Nomor ID",
      "Penawaran & Layanan Resmi",
      "Notifikasi Dukungan Pelanggan",
    )

  fun defaultTemplate(): EmailTemplateEntity {
    return EmailTemplateEntity(
      name = "Template Follow-Up Utama",
      subject = "Informasi Layanan untuk {{nama}} ({{nomor_id}})",
      headerTitle = "HaloAntri • Tindak Lanjut Panggilan",
      headerStyle = EmailHeaderStyle.SOLID_LEFT.key,
      accentColorHex = "#007A5E",
      backgroundStyle = EmailVisualTheme.CLEAN_LIGHT.key,
      bodyHtml =
        """
        <p>Halo <strong>{{nama}}</strong>,</p>
        <p>Kami telah mencatat data Anda pada sistem antrian kami:</p>
        <ul>
          <li><strong>Nomor HP:</strong> {{nomor_hp}}</li>
          <li><strong>Nomor ID:</strong> {{nomor_id}}</li>
          <li><strong>Email:</strong> {{email}}</li>
        </ul>
        <p>{{catatan}}</p>
        <p>Silakan balas email ini atau hubungi kami kembali apabila membutuhkan bantuan lebih lanjut.</p>
        """.trimIndent(),
      ctaLabel = "Hubungi via WhatsApp",
      ctaUrl = "https://wa.me/",
      footerText = "Dikirim otomatis melalui konsol HaloAntri.",
      useRawHtmlOnly = false,
      isDefault = true,
    )
  }

  private val indonesianLocale = Locale.forLanguageTag("id-ID")

  fun formatDate(millis: Long): String =
    SimpleDateFormat("dd MMMM yyyy", indonesianLocale).format(Date(millis))

  fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", indonesianLocale).format(Date(millis))

  fun formatDateTime(millis: Long): String =
    SimpleDateFormat("EEEE, dd MMMM yyyy • HH:mm", indonesianLocale).format(Date(millis))

  fun renderSubject(
    template: EmailTemplateEntity,
    contact: CallQueueItemEntity?,
    sentAtMillis: Long = System.currentTimeMillis(),
  ): String {
    return substitutePlaceholders(template.subject, contact, escapeHtml = false, sentAtMillis = sentAtMillis)
  }

  fun substituteTextPlaceholders(
    raw: String,
    contact: CallQueueItemEntity?,
    sentAtMillis: Long = System.currentTimeMillis(),
  ): String {
    return substitutePlaceholders(raw, contact, escapeHtml = false, sentAtMillis = sentAtMillis)
  }

  fun sanitizeHexColor(raw: String): String {
    val trimmed = raw.trim()
    val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return if (Regex("^#[0-9A-Fa-f]{6}$").matches(withHash)) withHash.uppercase() else "#007A5E"
  }

  fun sanitizeLogoSrc(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val lower = trimmed.lowercase()
    return when {
      lower.startsWith("https://") || lower.startsWith("http://") -> trimmed
      lower.startsWith("data:image/png;base64,") ||
        lower.startsWith("data:image/jpeg;base64,") ||
        lower.startsWith("data:image/jpg;base64,") ||
        lower.startsWith("data:image/webp;base64,") ||
        lower.startsWith("data:image/gif;base64,") -> trimmed
      else -> ""
    }
  }

  fun buildRenderedHtml(
    template: EmailTemplateEntity,
    contact: CallQueueItemEntity?,
    sentAtMillis: Long = System.currentTimeMillis(),
  ): String {
    val substitutedBody =
      substitutePlaceholders(template.bodyHtml, contact, escapeHtml = true, sentAtMillis = sentAtMillis)
    if (template.useRawHtmlOnly) {
      return substitutedBody
    }

    val theme = EmailVisualTheme.fromKey(template.backgroundStyle)
    val headerStyle = EmailHeaderStyle.fromKey(template.headerStyle)
    val safeHeader =
      escapeHtml(substitutePlaceholders(template.headerTitle, contact, escapeHtml = false, sentAtMillis = sentAtMillis))
    val safeFooter =
      escapeHtml(substitutePlaceholders(template.footerText, contact, escapeHtml = false, sentAtMillis = sentAtMillis))
    val safeCtaLabel =
      escapeHtml(substitutePlaceholders(template.ctaLabel, contact, escapeHtml = false, sentAtMillis = sentAtMillis))
    val safeCtaUrl =
      escapeHtml(substitutePlaceholders(template.ctaUrl, contact, escapeHtml = false, sentAtMillis = sentAtMillis))
    val safeHeaderLogo = escapeHtml(sanitizeLogoSrc(template.headerLogoUrl))
    val safeFooterLogo = escapeHtml(sanitizeLogoSrc(template.footerLogoUrl))
    val safeSentTimestamp = escapeHtml(formatDateTime(sentAtMillis))
    val accent = sanitizeHexColor(template.accentColorHex)

    val headerBlockHtml =
      buildHeaderHtml(
        headerStyle = headerStyle,
        headerTitle = safeHeader,
        headerLogo = safeHeaderLogo,
        accent = accent,
        theme = theme,
      )

    // Footer logo has NO border, NO background color, NO padding
    val footerLogoHtml =
      if (safeFooterLogo.isNotBlank()) {
        """<img src="$safeFooterLogo" alt="Logo Footer" style="max-height:40px; max-width:160px; display:block; margin:0 0 8px 0; border:none; outline:none; background:transparent; padding:0; object-fit:contain;" />"""
      } else {
        ""
      }

    val ctaHtml =
      if (safeCtaLabel.isNotBlank() && safeCtaUrl.isNotBlank()) {
        """
        <div style="margin: 24px 0 8px 0; text-align: center;">
          <a href="$safeCtaUrl" style="display:inline-block; background-color:$accent; color:#FFFFFF; text-decoration:none; font-weight:600; padding:12px 24px; border-radius:10px; font-size:14px;">$safeCtaLabel</a>
        </div>
        """.trimIndent()
      } else {
        ""
      }

    return """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      </head>
      <body style="margin:0; padding:16px; background-color:${theme.outerBg}; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color:${theme.textColor};">
        <div style="max-width:560px; margin:0 auto; background-color:${theme.cardBg}; border-radius:16px; overflow:hidden; border:1px solid rgba(128,128,128,0.18);">
          $headerBlockHtml
          <div style="padding:22px 24px; font-size:14px; line-height:1.6; color:${theme.textColor};">
            $substitutedBody
            $ctaHtml
          </div>
          <div style="padding:14px 24px; border-top:1px solid rgba(128,128,128,0.16); font-size:12px; color:${theme.mutedTextColor};">
            $footerLogoHtml
            <div>$safeFooter</div>
            <div style="margin-top:6px; font-size:11px; opacity:0.85;">Dikirim pada: $safeSentTimestamp</div>
          </div>
        </div>
      </body>
      </html>
    """.trimIndent()
  }

  private fun buildHeaderHtml(
    headerStyle: EmailHeaderStyle,
    headerTitle: String,
    headerLogo: String,
    accent: String,
    theme: EmailVisualTheme,
  ): String {
    // Pure logo image without border, radius, padding, or background color
    fun logoImg(marginStyle: String): String =
      if (headerLogo.isNotBlank()) {
        """<img src="$headerLogo" alt="Logo Header" style="max-height:52px; max-width:200px; display:block; margin:$marginStyle; border:none; outline:none; background:transparent; padding:0; object-fit:contain;" />"""
      } else {
        ""
      }

    return when (headerStyle) {
      EmailHeaderStyle.SOLID_LEFT ->
        """
        <div style="background-color:$accent; padding:20px 24px; color:#FFFFFF;">
          ${logoImg("0 0 10px 0")}
          <div style="font-size:18px; font-weight:700; letter-spacing:-0.2px;">$headerTitle</div>
        </div>
        """.trimIndent()

      EmailHeaderStyle.CENTERED_BADGE ->
        """
        <div style="background-color:$accent; padding:24px; color:#FFFFFF; text-align:center;">
          ${logoImg("0 auto 10px auto")}
          <div style="font-size:19px; font-weight:700; letter-spacing:-0.2px;">$headerTitle</div>
        </div>
        """.trimIndent()

      EmailHeaderStyle.GRADIENT_MODERN ->
        """
        <div style="background:linear-gradient(135deg, $accent 0%, #0F172A 100%); background-color:$accent; padding:22px 24px; color:#FFFFFF;">
          ${logoImg("0 0 10px 0")}
          <div style="font-size:18px; font-weight:700; letter-spacing:-0.2px;">$headerTitle</div>
        </div>
        """.trimIndent()

      EmailHeaderStyle.SPLIT_SIDE ->
        """
        <div style="background-color:$accent; padding:18px 24px; color:#FFFFFF;">
          <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="width:100%; border-collapse:collapse;">
            <tr>
              ${if (headerLogo.isNotBlank()) """<td style="vertical-align:middle; padding-right:12px; width:1%;">${logoImg("0")}</td>""" else ""}
              <td style="vertical-align:middle;">
                <div style="font-size:18px; font-weight:700; letter-spacing:-0.2px; color:#FFFFFF;">$headerTitle</div>
              </td>
            </tr>
          </table>
        </div>
        """.trimIndent()

      EmailHeaderStyle.MINIMAL_WHITE ->
        """
        <div style="background-color:${theme.cardBg}; padding:20px 24px 16px 24px; border-bottom:3px solid $accent;">
          ${logoImg("0 0 10px 0")}
          <div style="font-size:18px; font-weight:700; letter-spacing:-0.2px; color:$accent;">$headerTitle</div>
        </div>
        """.trimIndent()

      EmailHeaderStyle.SOFT_TINT ->
        """
        <div style="background-color:${theme.outerBg}; padding:20px 24px 18px 24px; border-bottom:2px solid $accent;">
          ${logoImg("0 0 10px 0")}
          <div style="font-size:18px; font-weight:700; letter-spacing:-0.2px; color:${theme.textColor};">$headerTitle</div>
        </div>
        """.trimIndent()
    }
  }

  fun buildCustomManualEmailHtml(
    bodyText: String,
    template: EmailTemplateEntity?,
    contact: CallQueueItemEntity?,
    wrapWithTemplateDesign: Boolean,
    sentAtMillis: Long = System.currentTimeMillis(),
  ): String {
    val escapedBodyText = escapeHtml(bodyText)
    val substitutedPlain =
      substitutePlaceholders(escapedBodyText, contact, escapeHtml = true, sentAtMillis = sentAtMillis)
    val paragraphHtml =
      substitutedPlain
        .trim()
        .split(Regex("""\r?\n\s*\r?\n"""))
        .filter { it.isNotBlank() }
        .joinToString("\n") { block ->
          val withBreaks = block.replace("\r\n", "\n").replace("\n", "<br/>")
          "<p style=\"margin:0 0 12px 0;\">$withBreaks</p>"
        }
        .ifBlank { "<p style=\"margin:0;\">-</p>" }

    if (wrapWithTemplateDesign && template != null) {
      val cleanTemplate =
        template.copy(
          bodyHtml = paragraphHtml,
          useRawHtmlOnly = false,
        )
      // bodyHtml is already escaped and formatted as <p> blocks; preserve its tags during buildRenderedHtml
      val theme = EmailVisualTheme.fromKey(cleanTemplate.backgroundStyle)
      val headerStyle = EmailHeaderStyle.fromKey(cleanTemplate.headerStyle)
      val safeHeader =
        escapeHtml(
          substitutePlaceholders(cleanTemplate.headerTitle, contact, escapeHtml = false, sentAtMillis = sentAtMillis)
        )
      val safeFooter =
        escapeHtml(
          substitutePlaceholders(cleanTemplate.footerText, contact, escapeHtml = false, sentAtMillis = sentAtMillis)
        )
      val safeCtaLabel =
        escapeHtml(
          substitutePlaceholders(cleanTemplate.ctaLabel, contact, escapeHtml = false, sentAtMillis = sentAtMillis)
        )
      val safeCtaUrl =
        escapeHtml(
          substitutePlaceholders(cleanTemplate.ctaUrl, contact, escapeHtml = false, sentAtMillis = sentAtMillis)
        )
      val safeHeaderLogo = escapeHtml(sanitizeLogoSrc(cleanTemplate.headerLogoUrl))
      val safeFooterLogo = escapeHtml(sanitizeLogoSrc(cleanTemplate.footerLogoUrl))
      val safeSentTimestamp = escapeHtml(formatDateTime(sentAtMillis))
      val accent = sanitizeHexColor(cleanTemplate.accentColorHex)

      val headerBlockHtml =
        buildHeaderHtml(
          headerStyle = headerStyle,
          headerTitle = safeHeader,
          headerLogo = safeHeaderLogo,
          accent = accent,
          theme = theme,
        )
      val footerLogoHtml =
        if (safeFooterLogo.isNotBlank()) {
          """<img src="$safeFooterLogo" alt="Logo Footer" style="max-height:40px; max-width:160px; display:block; margin:0 0 8px 0; border:none; outline:none; background:transparent; padding:0; object-fit:contain;" />"""
        } else {
          ""
        }
      val ctaHtml =
        if (safeCtaLabel.isNotBlank() && safeCtaUrl.isNotBlank()) {
          """
          <div style="margin: 24px 0 8px 0; text-align: center;">
            <a href="$safeCtaUrl" style="display:inline-block; background-color:$accent; color:#FFFFFF; text-decoration:none; font-weight:600; padding:12px 24px; border-radius:10px; font-size:14px;">$safeCtaLabel</a>
          </div>
          """.trimIndent()
        } else {
          ""
        }

      return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        </head>
        <body style="margin:0; padding:16px; background-color:${theme.outerBg}; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color:${theme.textColor};">
          <div style="max-width:560px; margin:0 auto; background-color:${theme.cardBg}; border-radius:16px; overflow:hidden; border:1px solid rgba(128,128,128,0.18);">
            $headerBlockHtml
            <div style="padding:22px 24px; font-size:14px; line-height:1.6; color:${theme.textColor};">
              $paragraphHtml
              $ctaHtml
            </div>
            <div style="padding:14px 24px; border-top:1px solid rgba(128,128,128,0.16); font-size:12px; color:${theme.mutedTextColor};">
              $footerLogoHtml
              <div>$safeFooter</div>
              <div style="margin-top:6px; font-size:11px; opacity:0.85;">Dikirim pada: $safeSentTimestamp</div>
            </div>
          </div>
        </body>
        </html>
      """.trimIndent()
    }

    val safeSentTimestamp = escapeHtml(formatDateTime(sentAtMillis))
    return """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      </head>
      <body style="margin:0; padding:16px; background-color:#FFFFFF; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color:#111B21; font-size:14px; line-height:1.6;">
        $paragraphHtml
        <div style="margin-top:18px; padding-top:10px; border-top:1px solid #E5E7EB; font-size:11px; color:#6B7280;">
          Dikirim pada: $safeSentTimestamp
        </div>
      </body>
      </html>
    """.trimIndent()
  }

  private fun substitutePlaceholders(
    raw: String,
    contact: CallQueueItemEntity?,
    escapeHtml: Boolean,
    sentAtMillis: Long = System.currentTimeMillis(),
  ): String {
    val name = contact?.name?.ifBlank { "Pelanggan" } ?: "Budi Santoso"
    val phone =
      when {
        contact == null -> "+62 812-3456-7890"
        contact.phoneNumber.isBlank() -> "-"
        else -> PhoneNumberExtractor.formatDisplayPhone(contact.phoneNumber)
      }
    val idNum = contact?.idNumber?.ifBlank { "-" } ?: "ID-2026-001"
    val email = contact?.email?.ifBlank { "-" } ?: "budi@contoh.com"
    val notes =
      contact?.notes?.ifBlank { contact.capturedMessage.ifBlank { "Terima kasih telah menghubungi kami." } }
        ?: "Contoh pesan tindak lanjut."
    val dateStr = formatDate(sentAtMillis)
    val timeStr = formatTime(sentAtMillis)
    val dateTimeStr = formatDateTime(sentAtMillis)

    fun wrap(v: String) = if (escapeHtml) escapeHtml(v) else v

    return raw
      .replace("{{nama}}", wrap(name))
      .replace("{{nomor_hp}}", wrap(phone))
      .replace("{{nomor_id}}", wrap(idNum))
      .replace("{{email}}", wrap(email))
      .replace("{{catatan}}", wrap(notes))
      .replace("{{tanggal}}", wrap(dateStr))
      .replace("{{waktu}}", wrap(timeStr))
      .replace("{{tanggal_waktu}}", wrap(dateTimeStr))
  }

  private fun escapeHtml(input: String): String {
    return input
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
  }
}
