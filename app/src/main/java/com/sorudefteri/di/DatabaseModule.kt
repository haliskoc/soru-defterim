package com.sorudefteri.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sorudefteri.data.local.SoruDatabase
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
                SoruDatabase.MIGRATION_4_5
            )
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
}
