package com.sorudefteri.di

import android.content.Context
import androidx.room.Room
import com.sorudefteri.data.local.SoruDatabase
import com.sorudefteri.data.local.dao.SoruDao
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
            .addMigrations(SoruDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    @Singleton
    fun provideSoruDao(db: SoruDatabase): SoruDao {
        return db.soruDao()
    }
}
