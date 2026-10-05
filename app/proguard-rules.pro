# ProGuard / R8 rules for Lirix

# Keep Room generated classes
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Domain models & Entities
-keep class com.lirix.app.domain.** { *; }
-keep class com.lirix.app.storage.** { *; }

# Strip verbose and debug logs in release builds (Architecture Section 8.2)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
