package com.sorudefteri.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sorudefteri.data.local.SoruDatabase
import com.sorudefteri.data.local.dao.BenzerSoruDao
import com.sorudefteri.data.local.dao.DenemeSinaviDao
import com.sorudefteri.data.local.dao.SoruChatDao
import com.sorudefteri.data.local.dao.SoruDao
import com.sorudefteri.data.local.dao.TestSonucDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SoruDatabase {
        return Room.databaseBuilder(
            context,
            SoruDatabase::class.java,
            SoruDatabase.DATABASE_NAME
        )
            .addMigrations(
                SoruDatabase.MIGRATION_1_2,
                SoruDatabase.MIGRATION_2_3,
                SoruDatabase.MIGRATION_3_4,
                SoruDatabase.MIGRATION_4_5,
                SoruDatabase.MIGRATION_5_6,
                SoruDatabase.MIGRATION_6_7,
                SoruDatabase.MIGRATION_7_8,
                SoruDatabase.MIGRATION_8_9,
                SoruDatabase.MIGRATION_9_10,
                SoruDatabase.MIGRATION_10_11,
                SoruDatabase.MIGRATION_11_12,
                SoruDatabase.MIGRATION_12_13
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON;")
                }
            })
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    @Provides
    @Singleton
    fun provideSoruDao(db: SoruDatabase): SoruDao {
        return db.soruDao()
    }

    @Provides
    @Singleton
    fun provideTestSonucDao(db: SoruDatabase): TestSonucDao {
        return db.testSonucDao()
    }

    @Provides
    @Singleton
    fun provideSoruChatDao(db: SoruDatabase): SoruChatDao {
        return db.soruChatDao()
    }

    @Provides
    @Singleton
    fun provideBenzerSoruDao(db: SoruDatabase): BenzerSoruDao {
        return db.benzerSoruDao()
    }

    @Provides
    @Singleton
    fun provideDenemeSinaviDao(db: SoruDatabase): DenemeSinaviDao {
        return db.denemeSinaviDao()
    }
}
