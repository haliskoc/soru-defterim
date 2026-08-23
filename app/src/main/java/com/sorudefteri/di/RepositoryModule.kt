package com.sorudefteri.di

import com.sorudefteri.data.ai.AiRepository as AiRepositoryImpl
import com.sorudefteri.data.repository.SoruRepository
import com.sorudefteri.data.repository.SoruRepositoryImpl
import com.sorudefteri.domain.repository.AiRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dependency injection module binding repository interfaces to implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSoruRepository(impl: SoruRepositoryImpl): SoruRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository
}
