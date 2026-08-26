package com.sorudefteri.di

import android.content.Context
import android.util.Log
import com.sorudefteri.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Core application dependency injection module.
 * Provides application context, JSON serializer, and OkHttpClient instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        // 10 MB HTTP cache — özellikle GET /models için offline fallback
        val cacheSize = 10L * 1024 * 1024 // 10 MB
        val cache = Cache(File(context.cacheDir, "http_cache"), cacheSize)

        // Offline cache interceptor: GET /models isteklerini 7 gün cache'le, çevrimdışıyken cache'den servis et
        val offlineCacheInterceptor = Interceptor { chain ->
            var request = chain.request()
            // listModels GET istekleri için cache-Control ekle
            if (request.method == "GET" && request.url.toString().contains("/models")) {
                request = request.newBuilder()
                    .cacheControl(CacheControl.Builder().maxAge(1, TimeUnit.DAYS).build())
                    .build()
            }
            val response = chain.proceed(request)
            // Sunucu cache header göndermese bile 1 saat client-side cache
            if (request.method == "GET" && request.url.toString().contains("/models")) {
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=3600")
                    .removeHeader("Pragma")
                    .build()
            } else {
                response
            }
        }

        val forceCacheOnOfflineInterceptor = Interceptor { chain ->
            var request = chain.request()
            // İnternet yoksa cache'den oku (max-stale 7 gün)
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val network = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(network)
                val isOnline = caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (!isOnline && request.method == "GET") {
                    request = request.newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build()
                }
            } catch (_: Exception) {
                // ConnectivityManager hatasında normal devam
            }
            chain.proceed(request)
        }

        // P0-1: Sadece DEBUG'da log, release'de NONE; ayrıca key redaction
        val redactingLogger = HttpLoggingInterceptor.Logger { message ->
            var redacted = message
            // URL ?key= parametresi
            redacted = redacted.replace(Regex("([?&]key=)[^&\\s]+"), "$1***REDACTED***")
            // x-goog-api-key header
            redacted = redacted.replace(
                Regex("x-goog-api-key:\\s*[^\\r\\n]+", RegexOption.IGNORE_CASE),
                "x-goog-api-key: ***REDACTED***"
            )
            // Authorization Bearer
            redacted = redacted.replace(
                Regex("Authorization:\\s*Bearer\\s+[^\\r\\n]+", RegexOption.IGNORE_CASE),
                "Authorization: Bearer ***REDACTED***"
            )
            Log.d("OkHttp", redacted)
        }

        val loggingInterceptor = HttpLoggingInterceptor(redactingLogger).apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            // Ekstra: hassas header'ları zaten loglamamak için redact (BODY seviyesine geçilse bile)
            redactHeader("Authorization")
            redactHeader("x-goog-api-key")
            redactHeader("X-Goog-Api-Key")
        }

        // ApiKey query'si loglara sızmasın diye ek interceptor - URL'yi log öncesi redact için değil,
        // asıl isteği header'a taşımak için GeminiProvider'da x-goog-api-key kullanılıyor.
        // Burada sadece savunma katmanı: eğer hala ?key= varsa, log seviyesini NONE yapıyoruz (release).
        val apiKeyQuerySanitizer = Interceptor { chain ->
            val request = chain.request()
            // İsteği değiştirmiyoruz, sadece loglama için değil; ileride query'den header'a taşımak istersek
            // burada yapılabilir. Şimdilik sadece geçişe izin ver.
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(forceCacheOnOfflineInterceptor)
            .addInterceptor(apiKeyQuerySanitizer)
            .addInterceptor(offlineCacheInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
