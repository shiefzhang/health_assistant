# Keep Room entities
-keep class com.healthassistant.data.model.** { *; }

# Keep OkHttp for WebDAV
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep JSON
-keep class org.json.** { *; }

# Keep EncryptedSharedPreferences
-keep class androidx.security.crypto.** { *; }
