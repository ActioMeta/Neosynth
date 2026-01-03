# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Keep all Retrofit service interfaces
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep Retrofit service interface methods
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep API service
-keep interface com.example.neosynth.data.remote.NavidromeApi { *; }
-keep interface com.example.neosynth.data.remote.NavidromeApiService { *; }

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep all Response classes and their fields (CRITICAL for Gson/Retrofit)
-keep class com.example.neosynth.data.remote.responses.** { *; }
-keepclassmembers class com.example.neosynth.data.remote.responses.** { *; }
-keep class com.example.neosynth.data.remote.dto.** { *; }
-keepclassmembers class com.example.neosynth.data.remote.dto.** { *; }

# Keep fields with SerializedName annotation
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

# Prevent obfuscation of classes with Gson annotations
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn com.google.errorprone.annotations.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.** {
    volatile <fields>;
}

# Media3/ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep data models (CRITICAL - estas clases se usan en serialización/deserialización)
-keep class com.example.neosynth.data.remote.responses.** { *; }
-keep class com.example.neosynth.data.remote.dto.** { *; }
-keep class com.example.neosynth.data.local.entities.** { *; }
-keep class com.example.neosynth.domain.model.** { *; }

# Keep all model classes used in Room and Retrofit
-keepclassmembers class com.example.neosynth.data.remote.responses.** { *; }
-keepclassmembers class com.example.neosynth.data.remote.dto.** { *; }
-keepclassmembers class com.example.neosynth.data.local.entities.** { *; }
-keepclassmembers class com.example.neosynth.domain.model.** { *; }

# Keep Kotlin data classes (preserve copy, componentN, etc.)
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    public synthetic <methods>;
}

# Keep Composables
-keep class com.example.neosynth.ui.** { *; }

# Keep service classes
-keep class * extends android.app.Service
-keep class * extends androidx.media3.session.MediaSessionService

# Keep BroadcastReceiver for Google Assistant
-keep class com.example.neosynth.receiver.** { *; }
-keep class * extends android.content.BroadcastReceiver

# Additional rules for debugging (can be removed for final release)
-printmapping mapping.txt
-keepattributes *Annotation*,Signature,Exception,LineNumberTable

# Keep source file names for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Don't warn about missing classes
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# CRITICAL: Keep all classes with @SerializedName annotations intact
-keepclasseswithmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep generic signatures for Retrofit/Gson
-keepattributes Signature
-keep class kotlin.coroutines.Continuation

# Keep R8 from breaking data classes
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep class kotlin.Metadata { *; }
