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
        val key = masterKey ?: provideMasterKey(context)
        if (key != null) {
            try {
                return EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_NAME,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences oluşturulamadı, keyset ve prefs temizlenip yeniden deneniyor", e)
                try {
                    // Bozulmuş eski prefs ve Tink keyset dosyalarını diskten tamamen temizle
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
                        context.deleteSharedPreferences("${ENCRYPTED_PREFS_NAME}__androidx_security_crypto_encrypted_prefs_key_keyset__")
                    } else {
                        val sharedPrefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
                        java.io.File(sharedPrefsDir, "$ENCRYPTED_PREFS_NAME.xml").delete()
                        java.io.File(sharedPrefsDir, "${ENCRYPTED_PREFS_NAME}__androidx_security_crypto_encrypted_prefs_key_keyset__.xml").delete()
                    }

                    return EncryptedSharedPreferences.create(
                        context,
                        ENCRYPTED_PREFS_NAME,
                        key,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (e2: Exception) {
                    Log.e(TAG, "EncryptedSharedPreferences kurtarma da başarısız oldu", e2)
                }
            }
        }

        // Cihaz Keystore donanımı desteklemiyorsa çökme önleyici fallback
        Log.w(TAG, "Donanımsal şifreleme kullanılamıyor, korumalı fallback aktif.")
        return context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
    }
}

