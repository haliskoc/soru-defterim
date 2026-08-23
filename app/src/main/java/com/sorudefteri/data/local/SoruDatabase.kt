package com.sorudefteri.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.entity.SoruEntity

/**
 * Room Veritabanı Tanımı
 */
@Database(
    entities = [SoruEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SoruDatabase : RoomDatabase() {

    abstract fun soruDao(): SoruDao

    companion object {
        const val DATABASE_NAME = "soru_defteri.db"
    }
}
