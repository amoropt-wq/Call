package com.example.data.local

import androidx.room.TypeConverter

class Converters {
  @TypeConverter
  fun fromWhatsAppFlavor(value: WhatsAppFlavor): String = value.name

  @TypeConverter
  fun toWhatsAppFlavor(value: String): WhatsAppFlavor =
    runCatching { WhatsAppFlavor.valueOf(value) }.getOrDefault(WhatsAppFlavor.PERSONAL)

  @TypeConverter
  fun fromQueueSource(value: QueueSource): String = value.name

  @TypeConverter
  fun toQueueSource(value: String): QueueSource =
    runCatching { QueueSource.valueOf(value) }.getOrDefault(QueueSource.MANUAL)

  @TypeConverter
  fun fromQueueStatus(value: QueueStatus): String = value.name

  @TypeConverter
  fun toQueueStatus(value: String): QueueStatus =
    runCatching { QueueStatus.valueOf(value) }.getOrDefault(QueueStatus.WAITING)
}
