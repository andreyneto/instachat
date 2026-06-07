# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.aneto.instachat.**$$serializer { *; }
-keepclassmembers class com.aneto.instachat.** {
    *** Companion;
}
-keepclasseswithmembers class com.aneto.instachat.** {
    kotlinx.serialization.KSerializer serializer(...);
}
