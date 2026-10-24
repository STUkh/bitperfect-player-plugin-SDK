package com.bitperfect.player.sdk.plugin;

interface IPluginReceiverSessionV1 {
    oneway void onPlaybackStateChanged(int state);
    oneway void onPositionTick(long positionMs, long durationMs);
    oneway void onDeviceFormatChanged(int sampleRate, int bitDepth);
    oneway void onBufferStateChanged(int bufferState);
    oneway void onCloseSession();
    oneway void onTrackTransition(String trackId, long durationMs);
    oneway void onQueueEnded();
    oneway void onDeviceVolumeChanged(float volumeFraction, boolean isMuted);
    oneway void onPlaybackError(String trackId, int errorCode, String errorMessage);
}
