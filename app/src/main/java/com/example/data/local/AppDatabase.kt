package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities =
    [
      WhatsAppAccountEntity::class,
      CallQueueItemEntity::class,
      CallLogEntity::class,
      EmailTemplateEntity::class,
      AudioClipEntity::class,
    ],
  version = 5,
  exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun haloAntriDao(): HaloAntriDao

  companion object {
    @Volatile private var INSTANCE: AppDatabase? = null

    val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE call_queue ADD COLUMN email TEXT NOT NULL DEFAULT ''")
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `email_templates` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `name` TEXT NOT NULL,
              `subject` TEXT NOT NULL,
              `headerTitle` TEXT NOT NULL,
              `accentColorHex` TEXT NOT NULL,
              `backgroundStyle` TEXT NOT NULL,
              `bodyHtml` TEXT NOT NULL,
              `ctaLabel` TEXT NOT NULL,
              `ctaUrl` TEXT NOT NULL,
              `footerText` TEXT NOT NULL,
              `useRawHtmlOnly` INTEGER NOT NULL,
              `isDefault` INTEGER NOT NULL,
              `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
          )
        }
      }

    val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE email_templates ADD COLUMN headerLogoUrl TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE email_templates ADD COLUMN footerLogoUrl TEXT NOT NULL DEFAULT ''")
          db.execSQL("ALTER TABLE call_queue ADD COLUMN senderName TEXT NOT NULL DEFAULT ''")
          db.execSQL(
            "UPDATE call_queue SET senderName = name WHERE source != 'MANUAL' AND senderName = ''"
          )
        }
      }

    val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            "ALTER TABLE email_templates ADD COLUMN headerStyle TEXT NOT NULL DEFAULT 'SOLID_LEFT'"
          )
        }
      }

    val MIGRATION_4_5 =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `audio_clips` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `name` TEXT NOT NULL,
              `originalFileName` TEXT NOT NULL,
              `filePath` TEXT NOT NULL,
              `mimeType` TEXT NOT NULL,
              `durationMs` INTEGER NOT NULL,
              `fileSizeBytes` INTEGER NOT NULL,
              `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
          )
        }
      }

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE
        ?: synchronized(this) {
          val instance =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "haloantri_database",
              )
              .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
              .build()
          INSTANCE = instance
          instance
        }
    }
  }
}
