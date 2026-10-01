# Retain exported Plugin Service and SDK entry points
-keep class * extends com.bitperfect.player.sdk.plugin.runtime.BitperfectSourcePlugin { <init>(); }
-keep class * extends com.bitperfect.player.sdk.plugin.runtime.BitperfectRendererPlugin { <init>(); }
-keep class * extends com.bitperfect.player.sdk.plugin.runtime.BitperfectDspPlugin { <init>(); }

# Keep DTO classes and Kotlinx serialization models
-keep class com.bitperfect.player.sdk.plugin.dto.** { *; }
-keep class com.bitperfect.player.core.plugin.page.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
