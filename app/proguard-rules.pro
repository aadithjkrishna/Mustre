# ==============================================================================
# Mustre Music Player - Production R8 / ProGuard Optimization Rules
# Optimized for minimal APK size, dead-code removal, and peak audio performance
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. R8 Optimization & Code Shrinking Passes
# ------------------------------------------------------------------------------
-optimizationpasses 5
-allowaccessmodification
-mergeinterfacesaggressively
-repackageclasses 'com.aadith.mustre.opt'

# Strip all logs in release build for peak performance, minimal overhead & maximum security
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
    public static int println(...);
}

# ------------------------------------------------------------------------------
# 2. Mustre Data Models & JSON Serialization (Gson)
# ------------------------------------------------------------------------------
# Prevent R8 from obfuscating fields used in serialization/deserialization
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.aadith.mustre.model.** { *; }
-keepclassmembers class com.aadith.mustre.model.** { *; }

-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** { *; }

# ------------------------------------------------------------------------------
# 3. AndroidX Media3 / ExoPlayer
# ------------------------------------------------------------------------------
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep audio codecs and extractors loaded via reflection
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.datasource.** { *; }

# ------------------------------------------------------------------------------
# 4. Glide Image Caching & Thumbnail Loading
# ------------------------------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# ------------------------------------------------------------------------------
# 5. OkHttp & Okio Networking
# ------------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ------------------------------------------------------------------------------
# 6. Android Support / AppCompat / Material Components
# ------------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# Keep custom drawables and custom view attributes
-keepclassmembers class * extends android.view.View {
    void set*(...);
    void get*(...);
}
