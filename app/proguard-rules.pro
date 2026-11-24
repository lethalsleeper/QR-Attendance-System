# Project-specific R8 rules.
# Firebase, Hilt, CameraX, ML Kit and kotlinx.serialization ship their own consumer rules.

# Keep serializable navigation routes and the QR payload DTO (kotlinx.serialization).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.example.qrattendance.**$$serializer { *; }
-keepclassmembers class com.example.qrattendance.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.qrattendance.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep source file names and line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
