package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.data.local.WhatsAppFlavor
import com.example.domain.PhoneNumberExtractor

sealed interface CallLaunchResult {
  data class LaunchedWhatsApp(val flavorName: String) : CallLaunchResult

  data class WhatsAppUnavailableFallback(val reason: String) : CallLaunchResult
}

object WhatsAppCallLauncher {

  fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
          packageName,
          PackageManager.PackageInfoFlags.of(0),
        )
      } else {
        @Suppress("DEPRECATION") context.packageManager.getPackageInfo(packageName, 0)
      }
      true
    } catch (_: PackageManager.NameNotFoundException) {
      false
    }
  }

  fun launchWhatsAppCallSession(
    context: Context,
    targetPhoneNumber: String,
    preferredFlavor: WhatsAppFlavor,
  ): CallLaunchResult {
    val digits = PhoneNumberExtractor.toWhatsAppDigits(targetPhoneNumber)
    if (digits.isBlank()) {
      return CallLaunchResult.WhatsAppUnavailableFallback("Nomor tujuan tidak valid.")
    }

    val targetPkg =
      when {
        isPackageInstalled(context, preferredFlavor.packageName) -> preferredFlavor.packageName
        isPackageInstalled(context, WhatsAppFlavor.PERSONAL.packageName) ->
          WhatsAppFlavor.PERSONAL.packageName
        isPackageInstalled(context, WhatsAppFlavor.BUSINESS.packageName) ->
          WhatsAppFlavor.BUSINESS.packageName
        else -> null
      }

    if (targetPkg == null) {
      return CallLaunchResult.WhatsAppUnavailableFallback(
        "Aplikasi WhatsApp belum terpasang di perangkat ini. Konsol panggilan & bisu senyap tetap aktif."
      )
    }

    val flavorLabel =
      if (targetPkg == WhatsAppFlavor.BUSINESS.packageName) {
        WhatsAppFlavor.BUSINESS.displayName
      } else {
        WhatsAppFlavor.PERSONAL.displayName
      }

    val directIntent =
      Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=$digits")).apply {
        setPackage(targetPkg)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

    return try {
      context.startActivity(directIntent)
      CallLaunchResult.LaunchedWhatsApp(flavorLabel)
    } catch (_: Exception) {
      val webIntent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$digits")).apply {
          setPackage(targetPkg)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      try {
        context.startActivity(webIntent)
        CallLaunchResult.LaunchedWhatsApp(flavorLabel)
      } catch (_: Exception) {
        CallLaunchResult.WhatsAppUnavailableFallback(
          "Gagal membuka $flavorLabel secara langsung. Konsol panggilan lokal tetap aktif."
        )
      }
    }
  }
}
