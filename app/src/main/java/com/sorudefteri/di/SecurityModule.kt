package com.sorudefteri.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Güvenlik Modülü: API anahtarları için EncryptedSharedPreferences sağlar.
 * AES256-GCM ile şifrelenmiş store, MasterKey/Keystore hatası durumunda
 * otomatik kurtarma ve çökmesiz güvenli fallback mekanizmasına sahiptir.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    private const val ENCRYPTED_PREFS_NAME = "soru_defteri_secure_prefs"
    private const val TAG = "SecurityModule"

    @Provides
    @Singleton
    fun provideMasterKey(@ApplicationContext context: Context): MasterKey? {
        return try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "MasterKey oluşturulamadı (KeyStore hatası), null dönülüyor", e)
            null
        }
    }

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context,
        masterKey: MasterKey?
    ): SharedPreferences {
        // 1. MasterKey mevcutsa EncryptedSharedPreferences dene
        if (masterKey != null) {
            try {
                return EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences oluşturulamadı, temizlenip yeniden deneniyor", e)
                try {
                    // Bozulmuş eski prefs dosyasını temizle ve sıfırdan dene
                    context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .commit()

                    return EncryptedSharedPreferences.create(
                        context,
                        ENCRYPTED_PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "EncryptedSharedPreferences kurtarma başarısız, standart SharedPreferences fallback", e2)
                }
            }
        }

        // 2. Fallback: Standart SharedPreferences (cihaz Keystore'u bozuk veya desteklemiyorsa çökme önlenir)
        Log.w(TAG, "Güvenlik fallback devreye girdi: Standart SharedPreferences kullanılıyor.")
        return context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
    }
}

