# J.A.R.V.I.S. ProGuard / R8 Keep Rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room entities and DAO generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Moshi & JsonCodec generic map/list serialization
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# OkHttp / Okio optional Conscrypt / JSR-305 compile-time annotations
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# CameraX & Kotlin Coroutines
-keep class androidx.camera.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
