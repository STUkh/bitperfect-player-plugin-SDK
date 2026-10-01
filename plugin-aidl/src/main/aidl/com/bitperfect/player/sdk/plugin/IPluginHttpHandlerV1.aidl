package com.bitperfect.player.sdk.plugin;

import com.bitperfect.player.sdk.plugin.dto.HttpResponseDto;

interface IPluginHttpHandlerV1 {
    HttpResponseDto handleHttpRequest(String method, String path, in byte[] body);
}
