package com.bitperfect.player.sdk.plugin;

import com.bitperfect.player.sdk.plugin.dto.TrackRefDto;

interface IHostReceiverControllerV1 {
    oneway void playTrack(in TrackRefDto track, long startPositionMs);
    oneway void pause();
    oneway void resume();
    oneway void seek(long positionMs);
    oneway void setVolume(float volumeFraction);
    oneway void updateQueue(in List<TrackRefDto> tracks, int activeIndex);
    oneway void stop();
}
