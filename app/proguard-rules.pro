# Proguard rules for ScreenRecorder

# Keep all project components, services, activities, receivers, and models
-keep class com.system.screenrecorder.** { *; }
-keepclassmembers class com.system.screenrecorder.** { *; }

# Keep Compose runtime and UI components
-keep class androidx.compose.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep Lifecycle and Activity components
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.core.** { *; }
