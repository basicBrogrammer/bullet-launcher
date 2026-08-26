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
#-keepattributes SourceFile,LineNumberTable

# Sentry (crash reporting)
-keepattributes LineNumberTable,SourceFile
-dontwarn io.sentry.**
-keep class io.sentry.** { *; }

# ML Kit GenAI / Gemini Nano (AICore client)
-keep class com.google.mlkit.genai.** { *; }
-dontwarn com.google.mlkit.genai.**

# App Functions (system assistant tools)
-keep class androidx.appfunctions.** { *; }
-keep class app.olauncher.ai.** { *; }
-dontwarn androidx.appfunctions.**