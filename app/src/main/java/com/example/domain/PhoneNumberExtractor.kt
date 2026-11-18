package com.example.domain

data class CapturedContactData(
  val senderName: String,
  val contactName: String,
  val phoneNumbers: List<String>,
  val idOrCardNumber: String,
  val email: String,
)

object PhoneNumberExtractor {

  // Matches Indonesian mobile numbers (+628..., 628..., 08...) and international numbers (+...)
  private val phoneCandidateRegex =
    Regex(
      """(?<!\d)(?:\+62|62|0)[\s\-().]*8[1-9](?:[\s\-().]*\d){6,12}(?!\d)|(?<!\d)\+[1-9](?:[\s\-().]*\d){7,14}(?!\d)"""
    )

  // Matches "Nama PURWANTO", "Nama: PURWANTO", "AGUNG Nama PURWANTO", etc.
  // Stops at newline or before the next field label (Nomor, No, HP, Kartu, ID, Email, Yahoo).
  private val nameCandidateRegex =
    Regex(
      """(?im)(?:^|[\s,;])Nama(?:\s+Lengkap)?\s*[:=\-]?\s*(.+?)(?=\s*(?:\r?\n|$|\b(?:Nomor|No\.?|HP|Telp|Telepon|WA|WhatsApp|Kartu|ID|Email|E-mail|Yaho+)\b))"""
    )

  // Matches "Nomor kartu 4799 3111 0126 8348", "No. Kartu: ...", "Card: ..."
  private val cardNumberRegex =
    Regex(
      """(?im)(?<![.\w])(?:(?:nomor|no\.?)\s*kartu|card(?:\s*number)?|no\.?\s*card)\s*[:=\-#]?\s*([0-9][0-9\s\-]{7,29}[0-9])"""
    )

  private val idCandidateRegex =
    Regex(
      """(?im)(?<![.\w])(?:no\.?\s*id|nomor\s*id|id\s*pelanggan|id\s*member|nik|id(?=\s*[:=\-#]|\s+[A-Z0-9\-_]*\d))\s*[:=\-#]?\s*([A-Z0-9][A-Z0-9\-_/\s]{2,26}?)(?=\s*(?:\r?\n|$|\b(?:Nomor|No\.?|HP|Telp|Nama|Email|E-mail|Yaho+)\b))"""
    )

  // Fallback standalone 16-digit (4x4) card number pattern like 4799 3111 0126 8348
  private val groupedCardFallbackRegex =
    Regex("""(?<!\d)\d{4}[\s\-]\d{4}[\s\-]\d{4}[\s\-]\d{4}(?!\d)""")

  private val senderLineRegex =
    Regex("""(?im)^\s*(?:pengirim|sender|dari)\s*[:=\-].*$""")

  private val emailRegex =
    Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

  fun extractEmails(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return emailRegex.findAll(text).map { it.value.trim() }.distinct().toList()
  }

  fun extractContactName(text: String): String {
    if (text.isBlank()) return ""
    val match = nameCandidateRegex.find(text) ?: return ""
    return match.groupValues.getOrNull(1)?.trim()?.trim(':', '-', ' ')?.take(80).orEmpty()
  }

  fun extractCardOrIdNumber(text: String): String {
    if (text.isBlank()) return ""
    cardNumberRegex.find(text)?.groupValues?.getOrNull(1)?.trim()?.let {
      if (it.isNotBlank()) return it.replace(Regex("""\s+"""), " ")
    }
    idCandidateRegex.find(text)?.groupValues?.getOrNull(1)?.trim()?.let {
      if (it.isNotBlank()) return it.replace(Regex("""\s+"""), " ")
    }
    groupedCardFallbackRegex.find(text)?.value?.trim()?.let {
      if (it.isNotBlank()) return it.replace(Regex("""\s+"""), " ")
    }
    return ""
  }

  fun extractIdNumber(text: String): String = extractCardOrIdNumber(text)

  fun extractPhoneNumbers(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    // Strip explicit sender lines and card/ID spans so card digits (even containing 08/628)
    // and sender lines are NEVER extracted as callable phone numbers.
    var sanitized = senderLineRegex.replace(text, " ")
    sanitized = cardNumberRegex.replace(sanitized, " ")
    sanitized = idCandidateRegex.replace(sanitized, " ")
    sanitized = groupedCardFallbackRegex.replace(sanitized, " ")

    val results = linkedSetOf<String>()
    for (match in phoneCandidateRegex.findAll(sanitized)) {
      val normalized = normalizePhoneNumber(match.value)
      val digitCount = normalized.count { it.isDigit() }
      if (digitCount in 9..15) {
        results.add(normalized)
      }
    }
    return results.toList()
  }

  /**
   * Parses incoming message data while keeping [senderTitle] strictly in [CapturedContactData.senderName]
   * and NEVER treating the sender's phone number as a target phone number to call.
   */
  fun extractStructuredContact(senderTitle: String, messageText: String): CapturedContactData {
    val rawSender = senderTitle.trim()
    val extractedName = extractContactName(messageText)
    val extractedCardOrId = extractCardOrIdNumber(messageText)
    val extractedEmail = extractEmails(messageText).firstOrNull().orEmpty()
    val extractedPhones = extractPhoneNumbers(messageText)

    // If senderTitle is blank/generic and the message starts with e.g. "AGUNG Nama PURWANTO",
    // capture the prefix ("AGUNG") as senderName while keeping "PURWANTO" as contactName.
    val prefixBeforeNama =
      Regex("""(?im)^\s*([^\r\n]+?)\s+Nama(?:\s+Lengkap)?\s*[:=\-]?\s*""")
        .find(messageText)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()

    val resolvedSender =
      when {
        rawSender.isNotBlank() && !rawSender.equals("WhatsApp Web", ignoreCase = true) -> rawSender
        prefixBeforeNama.isNotBlank() -> prefixBeforeNama
        else -> rawSender
      }

    val resolvedName =
      when {
        extractedName.isNotBlank() -> extractedName
        resolvedSender.isNotBlank() && extractPhoneNumbers(resolvedSender).isEmpty() -> resolvedSender
        else -> ""
      }

    return CapturedContactData(
      senderName = resolvedSender,
      contactName = resolvedName,
      phoneNumbers = extractedPhones,
      idOrCardNumber = extractedCardOrId,
      email = extractedEmail,
    )
  }

  fun normalizePhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    val hasPlus = trimmed.startsWith("+")
    val digitsOnly = trimmed.filter { it.isDigit() }
    if (digitsOnly.isEmpty()) return ""

    return when {
      digitsOnly.startsWith("62") && digitsOnly.length >= 10 -> "+$digitsOnly"
      digitsOnly.startsWith("08") && digitsOnly.length >= 9 -> "+62${digitsOnly.drop(1)}"
      digitsOnly.startsWith("8") && digitsOnly.length in 9..13 -> "+62$digitsOnly"
      hasPlus -> "+$digitsOnly"
      else -> "+$digitsOnly"
    }
  }

  fun toWhatsAppDigits(phoneNumber: String): String {
    return normalizePhoneNumber(phoneNumber).filter { it.isDigit() }
  }

  fun formatDisplayPhone(raw: String): String {
    val normalized = normalizePhoneNumber(raw)
    if (normalized.isBlank()) return raw
    val digits = normalized.removePrefix("+")
    return if (digits.startsWith("62") && digits.length >= 10) {
      val local = digits.removePrefix("62")
      val part1 = local.take(3)
      val part2 = local.drop(3).take(4)
      val part3 = local.drop(7)
      buildString {
        append("+62 ")
        append(part1)
        if (part2.isNotEmpty()) append("-").append(part2)
        if (part3.isNotEmpty()) append("-").append(part3)
      }
    } else {
      normalized
    }
  }
}
