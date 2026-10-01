package com.bitperfect.player.sdk.plugin;

import com.bitperfect.player.sdk.plugin.dto.DspStageInfoDto;

interface IPluginDspSessionV1 {
    DspStageInfoDto getStageInfo() = 1;
    void reset() = 2;
    void close() = 3;
}
