# Keep SQLCipher classes and JNI descriptors
-keep,includedescriptorclasses class net.zetetic.database.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.** { *; }

# Keep native methods
-keepclasseswithmembernames class net.zetetic.database.** {
    native <methods>;
}

# Prevent warnings for optional Android APIs
-dontwarn net.zetetic.database.**

# Support Room with annontation-based reflection support
-keepattributes *Annotation*