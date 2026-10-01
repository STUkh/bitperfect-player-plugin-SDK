# Bitperfect Player — Plugin AIDL & DTO ProGuard Rules
# AIDL interfaces and Parcelable DTOs cross process boundaries via Binder IPC.
# Class names, method names, and Parcelable CREATORs must NEVER be obfuscated.

-keep interface com.bitperfect.player.sdk.plugin.IBitperfectPluginV1 { *; }
-keep interface com.bitperfect.player.sdk.plugin.IPluginHostCallbackV1 { *; }
-keep class com.bitperfect.player.sdk.plugin.IBitperfectPluginV1$Stub { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginHostCallbackV1$Stub { *; }
-keep class com.bitperfect.player.sdk.plugin.IBitperfectPluginV1$Default { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginHostCallbackV1$Default { *; }
-keep interface com.bitperfect.player.sdk.plugin.IPluginDspSessionV1 { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginDspSessionV1$Stub { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginDspSessionV1$Default { *; }
-keep interface com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1 { *; }
-keep class com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1$Stub { *; }
-keep class com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1$Default { *; }
-keep interface com.bitperfect.player.sdk.plugin.IPluginHttpHandlerV1 { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginHttpHandlerV1$Stub { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginHttpHandlerV1$Default { *; }
-keep interface com.bitperfect.player.sdk.plugin.IPluginNetworkGatewayV1 { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginNetworkGatewayV1$Stub { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginNetworkGatewayV1$Default { *; }
-keep interface com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1 { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1$Stub { *; }
-keep class com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1$Default { *; }

-keep class com.bitperfect.player.sdk.plugin.dto.** { *; }
-keepclassmembers class com.bitperfect.player.sdk.plugin.dto.** {
    public static final android.os.Parcelable$Creator *;
    public void writeToParcel(android.os.Parcel, int);
    public int describeContents();
}
