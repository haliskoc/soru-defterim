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
    version = 3,
    exportSchema = false
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
    }
}

