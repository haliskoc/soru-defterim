package com.sorudefteri.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.dao.TestSonucDao
import com.sorudefteri.data.local.entity.SoruEntity
import com.sorudefteri.data.local.entity.TestSonucEntity

/**
 * Room Veritabanı Tanımı
 */
@Database(
    entities = [SoruEntity::class, TestSonucEntity::class],
    version = 5,
    exportSchema = true
)
abstract class SoruDatabase : RoomDatabase() {

    abstract fun soruDao(): SoruDao
    abstract fun testSonucDao(): TestSonucDao

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
    }
}

