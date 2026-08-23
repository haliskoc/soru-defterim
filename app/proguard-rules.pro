# Proguard rules for Soru Defterim
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Database *;
    @androidx.room.Entity *;
}
-keepattributes *Annotation*
