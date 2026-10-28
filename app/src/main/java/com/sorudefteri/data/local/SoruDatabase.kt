package com.sorudefteri.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sorudefteri.data.local.dao.BenzerSoruDao
import com.sorudefteri.data.local.dao.DenemeSinaviDao
import com.sorudefteri.data.local.dao.SoruChatDao
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.dao.TestSonucDao
import com.sorudefteri.data.local.entity.BenzerSoruEntity
import com.sorudefteri.data.local.entity.DenemeSinaviEntity
import com.sorudefteri.data.local.entity.SoruChatMessageEntity
import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.data.local.entity.TestSonucEntity
import com.sorudefteri.data.local.entity.SoruFtsEntity

/**
 * Room Veritabanı Tanımı
 */
@Database(
    entities = [
        SoruEntity::class,
        TestSonucEntity::class,
        SoruChatMessageEntity::class,
        SoruFtsEntity::class,
        BenzerSoruEntity::class,
        DenemeSinaviEntity::class
    ],
    version = 13,
    exportSchema = true
)
abstract class SoruDatabase : RoomDatabase() {

    abstract fun soruDao(): SoruDao
    abstract fun testSonucDao(): TestSonucDao
    abstract fun soruChatDao(): SoruChatDao
    abstract fun benzerSoruDao(): BenzerSoruDao
    abstract fun denemeSinaviDao(): DenemeSinaviDao

    companion object {
        const val DATABASE_NAME = "soru_defteri.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sorular ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sorular ADD COLUMN reviewStage INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sorular ADD COLUMN nextReviewDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE sorular ADD COLUMN lastReviewedAt INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `test_sonuclari` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `totalQuestions` INTEGER NOT NULL,
                        `correctCount` INTEGER NOT NULL,
                        `wrongCount` INTEGER NOT NULL,
                        `emptyCount` INTEGER NOT NULL,
                        `netCount` REAL NOT NULL,
                        `successRate` INTEGER NOT NULL,
                        `totalTimeSeconds` INTEGER NOT NULL,
                        `examType` TEXT,
                        `completedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sorular ADD COLUMN kaynakYayin TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sorular ADD COLUMN kaynakDeneme TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sorular ADD COLUMN kaynakYil INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE sorular ADD COLUMN kaynakSoruNo INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE sorular ADD COLUMN ocrMetni TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sorular ADD COLUMN isOcrUsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sorular ADD COLUMN questionText TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sorular ADD COLUMN studentAnswer TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sorular ADD COLUMN difficulty TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sorular_nextReviewDate` ON `sorular` (`nextReviewDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sorular_reviewStage` ON `sorular` (`reviewStage`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `soru_chat_mesajlari` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `soruId` INTEGER NOT NULL,
                        `sender` TEXT NOT NULL,
                        `messageText` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`soruId`) REFERENCES `sorular`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_soru_chat_mesajlari_soruId` ON `soru_chat_mesajlari` (`soruId`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `sorular_fts` USING FTS4(`topic`, `note`, `ocrMetni`, `questionText`, `aiExplanation`, content=`sorular`)")
                db.execSQL("INSERT INTO `sorular_fts`(`docid`, `topic`, `note`, `ocrMetni`, `questionText`, `aiExplanation`) SELECT `id`, `topic`, `note`, `ocrMetni`, `questionText`, `aiExplanation` FROM `sorular`")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Soruya bağlı kalıcı benzer sorular tablosu
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `benzer_sorular` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `soruId` INTEGER NOT NULL,
                        `questionText` TEXT NOT NULL,
                        `optionsJson` TEXT NOT NULL,
                        `correctAnswer` TEXT NOT NULL,
                        `solution` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`soruId`) REFERENCES `sorular`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_benzer_sorular_soruId` ON `benzer_sorular` (`soruId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_benzer_sorular_createdAt` ON `benzer_sorular` (`createdAt`)")

                // 2) v7->v8 ile gelen cihazlarda eksik kalan FTS senkron tetikleyicilerini onar
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_sorular_fts_BEFORE_UPDATE BEFORE UPDATE ON `sorular` BEGIN DELETE FROM `sorular_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_sorular_fts_BEFORE_DELETE BEFORE DELETE ON `sorular` BEGIN DELETE FROM `sorular_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_sorular_fts_AFTER_UPDATE AFTER UPDATE ON `sorular` BEGIN INSERT INTO `sorular_fts`(`docid`, `topic`, `note`, `ocrMetni`, `questionText`, `aiExplanation`) VALUES (NEW.`rowid`, NEW.`topic`, NEW.`note`, NEW.`ocrMetni`, NEW.`questionText`, NEW.`aiExplanation`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_sorular_fts_AFTER_INSERT AFTER INSERT ON `sorular` BEGIN INSERT INTO `sorular_fts`(`docid`, `topic`, `note`, `ocrMetni`, `questionText`, `aiExplanation`) VALUES (NEW.`rowid`, NEW.`topic`, NEW.`note`, NEW.`ocrMetni`, NEW.`questionText`, NEW.`aiExplanation`); END")
                // FTS içeriğini mevcut sorularla yeniden eşitle (hayalet/bayat kayıtları temizle)
                try {
                    db.execSQL("DELETE FROM `sorular_fts`")
                    db.execSQL("INSERT INTO `sorular_fts`(`docid`, `topic`, `note`, `ocrMetni`, `questionText`, `aiExplanation`) SELECT `id`, `topic`, `note`, `ocrMetni`, `questionText`, `aiExplanation` FROM `sorular`")
                } catch (_: Exception) {
                }
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Sorular tablosuna Ses Notu ve FSRS alanlarını ekle
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `audioPath` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `fsrsStability` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `fsrsDifficulty` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `fsrsReps` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `fsrsLapses` INTEGER NOT NULL DEFAULT 0")

                // 2. Deneme Sınavları tablosu
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `denemeler` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `baslik` TEXT NOT NULL,
                        `sinavTuru` TEXT NOT NULL,
                        `tarih` INTEGER NOT NULL,
                        `toplamNet` REAL NOT NULL,
                        `dersNetleriJson` TEXT NOT NULL,
                        `notlar` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_denemeler_sinavTuru` ON `denemeler` (`sinavTuru`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_denemeler_tarih` ON `denemeler` (`tarih`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_denemeler_createdAt` ON `denemeler` (`createdAt`)")
            }
        }

        // Akıllı içe aktarma: içerik parmak izi (dedup/merge) kolonu
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `contentHash` TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sorular_contentHash` ON `sorular` (`contentHash`)")
            }
        }

        // Cihazlar-arası eşleşme: evrensel kimlik + soft-delete
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `evrenselId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
                // Önce backfill (boş string'ler unique index'i ihlal eder), sonra unique index
                db.execSQL(
                    "UPDATE `sorular` SET `evrenselId` = lower(hex(randomblob(16))) " +
                        "WHERE `evrenselId` = '' OR `evrenselId` IS NULL"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sorular_evrenselId` ON `sorular` (`evrenselId`)")
            }
        }

        // Deneme sınavı & optik form & çift yönlü soru bağı alanları
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Sorular tablosuna denemeId ve denemeSoruNo ekle
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `denemeId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `sorular` ADD COLUMN `denemeSoruNo` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sorular_denemeId` ON `sorular` (`denemeId`)")

                // 2. Denemeler tablosuna optik, yayınevi ve soru detay alanlarını ekle
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `testId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `yayinEvi` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `sureDakika` INTEGER NOT NULL DEFAULT 165")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `toplamSoru` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `toplamDogru` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `toplamYanlis` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `toplamBos` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `cevapAnahtariJson` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `ogrenciCevaplariJson` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `denemeler` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `denemeler` SET `updatedAt` = `createdAt` WHERE `updatedAt` = 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_denemeler_testId` ON `denemeler` (`testId`)")
            }
        }
    }
}

