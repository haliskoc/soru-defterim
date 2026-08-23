package com.sorudefteri.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.entity.SoruEntity

/**
 * Room Veritabanı Tanımı
 */
@Database(
    entities = [SoruEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SoruDatabase : RoomDatabase() {

    abstract fun soruDao(): SoruDao

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
    }
}

