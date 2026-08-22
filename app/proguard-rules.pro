# Room / Hilt / WorkManager keep rules are shipped by the libraries themselves.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault

# kotlinx.serialization
-keepclassmembers class hu.padlas.** {
    *** Companion;
}
-keepclasseswithmembers class hu.padlas.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class hu.padlas.**$$serializer { *; }

# ML Kit text recognition models are resolved reflectively.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
