package com.bitperfect.player.sdk.plugin;

import com.bitperfect.player.sdk.plugin.IPluginHttpHandlerV1;

interface IPluginNetworkGatewayV1 {
    int registerServiceEndpoint(
        String serviceType,
        String serviceName,
        in List<String> paths,
        in Map<String, String> txtAttributes,
        in IPluginHttpHandlerV1 handler
    );

    void unregisterServiceEndpoint(int registrationId);
}
