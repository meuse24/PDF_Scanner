# Preserve line numbers in stack traces for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── PdfBox-Android ────────────────────────────────────────────────────────────
# PdfBox loads fonts, CMaps and encoding tables via reflection and classpath
# scanning; keeping all com.tom_roush classes prevents runtime ClassNotFound errors.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.apache.**

# ── Coroutines (internal compiler-generated classes) ─────────────────────────
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
