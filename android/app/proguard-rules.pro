# Keep Room entities
-keep class com.healthassistant.data.model.** { *; }

# Keep OkHttp for WebDAV
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep JSON
-keep class org.json.** { *; }

# Keep EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }

# Suppress R8 warnings for compile-time annotations used by Room
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
