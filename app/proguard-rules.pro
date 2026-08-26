# P0-1 Güvenlik - R8 / ProGuard Kuralları
# Keep class members with Room annotations
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Database *;
    @androidx.room.Entity *;
}
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature

# Room - keep DAO implementations & Paging
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.RoomDatabase
-keep class androidx.room.paging.** { *; }

# AndroidX Paging 3
-keep class androidx.paging.** { *; }
-dontwarn androidx.paging.**

# AndroidX WorkManager & Hilt Work
-keep class androidx.work.** { *; }
-keep class androidx.hilt.work.** { *; }
-keep class * extends androidx.work.ListenableWorker {
-    public <init>(android.content.Context, androidx.work.WorkerParameters);
-}
-keep class * extends androidx.work.Worker {
-    public <init>(android.content.Context, androidx.work.WorkerParameters);
-}
-keep class * extends androidx.work.CoroutineWorker {
-    public <init>(android.content.Context, androidx.work.WorkerParameters);
-}
-dontwarn androidx.work.**

# Google ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.vision.**

# Kotlin Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$$serializer {
    <fields>;
    <methods>;
}
-keep class com.sorudefteri.data.ai.dto.** { *; }
-keep class com.sorudefteri.data.backup.** { *; }
-keep class com.sorudefteri.domain.model.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}
-keep class androidx.hilt.** { *; }

# Coil 3
-keep class coil.** { *; }
-keep class io.coil.** { *; }
-dontwarn coil.**
-dontwarn io.coil.**

# OkHttp & Retrofit
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okio.** { *; }

# Coroutines
-keep class kotlinx.coroutines.** { *; }

# Security Crypto (Tink)
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**

# AndroidX Security fallback keep
-keep class androidx.security.** { *; }

# Generic keep for @Keep
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

