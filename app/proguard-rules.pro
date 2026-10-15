# Room, Hilt, WorkManager and kotlinx.serialization ship their own consumer keep rules.

# ML Kit creates its component registrars by reflection (no-arg constructor). R8 full mode strips those
# constructors, which crashes the barcode scanner in release builds with NoSuchMethodException.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-dontwarn com.google.mlkit.**
