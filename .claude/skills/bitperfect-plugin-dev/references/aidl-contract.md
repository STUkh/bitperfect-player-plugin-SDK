# Bitperfect Player Out-of-Process AIDL Contract Reference (API Level 7)

Authoritative AIDL contract specification for Bitperfect Player plugins. All transactions use explicit, append-only transaction IDs.

Package: `com.bitperfect.player.sdk.plugin`

---

## 1. `IBitperfectPluginV1`

The primary interface exposed by the plugin service bound via intent `com.bitperfect.plugin.BIND_PLUGIN`.

```aidl
package com.bitperfect.player.sdk.plugin;

import com.bitperfect.player.sdk.plugin.dto.StreamDescriptorDto;
import com.bitperfect.player.sdk.plugin.dto.TrackRefDto;
import com.bitperfect.player.sdk.plugin.dto.BrowseResultDto;
import com.bitperfect.player.sdk.plugin.dto.SearchResultDto;
import com.bitperfect.player.sdk.plugin.dto.AlbumDto;
import com.bitperfect.player.sdk.plugin.dto.RendererInfoDto;
import com.bitperfect.player.sdk.plugin.dto.PageDescriptorDto;
import com.bitperfect.player.sdk.plugin.dto.ActionResultDto;
import com.bitperfect.player.sdk.plugin.dto.DspSessionOfferDto;
import com.bitperfect.player.sdk.plugin.dto.DspSessionDescriptorDto;
import com.bitperfect.player.sdk.plugin.IPluginHostCallbackV1;
import com.bitperfect.player.sdk.plugin.IHostReceiverControllerV1;
import com.bitperfect.player.sdk.plugin.IPluginReceiverSessionV1;

interface IBitperfectPluginV1 {
    // API level compiled against; host rejects out-of-range versions.
    int getApiVersion() = 1;

    // Bitmask of capability flags (SOURCE=1, METADATA=2, OUTPUT_RENDERER=4, PAGES=256, DSP=512, etc.).
    int getCapabilities() = 2;

    // SOURCE: resolve a playable stream descriptor for a plugin-scoped track reference.
    @nullable StreamDescriptorDto resolveStream(in TrackRefDto track, int maxBitDepth, int maxSampleRate) = 3;

    // SOURCE: browse a directory/folder node; null folderId browses root.
    @nullable BrowseResultDto browse(@nullable String folderId, @nullable String pageToken) = 4;

    // SOURCE: search catalog with free-text query.
    @nullable SearchResultDto search(String query, @nullable String pageToken) = 5;

    // SOURCE: retrieve full album metadata and track list.
    @nullable AlbumDto getAlbum(String albumId) = 6;

    // SOURCE: resolve an external shared URL into a plugin TrackRefDto; null if unhandled.
    @nullable TrackRefDto parseUrl(String url) = 7;

    // OUTPUT_RENDERER: list playback targets currently discovered by the plugin.
    List<RendererInfoDto> listRenderers() = 8;

    // OUTPUT_RENDERER: initiate playback of host-served media URL on a remote renderer.
    boolean playOnRenderer(String rendererId, in TrackRefDto track, String hostMediaUrl) = 9;

    // OUTPUT_RENDERER: preload next track URL for gapless transitions.
    boolean enqueueNextOnRenderer(String rendererId, in TrackRefDto track, String hostMediaUrl) = 10;

    // Lifecycle: register host-side service callbacks.
    void registerHostCallback(in IPluginHostCallbackV1 callback) = 11;

    // Lifecycle: unregister host callbacks upon unbinding.
    void unregisterHostCallback(in IPluginHostCallbackV1 callback) = 12;

    // PAGES: render declarative SDUI page descriptor for routeId; null if route does not exist.
    @nullable PageDescriptorDto renderPage(String routeId, @nullable String paramsJson, @nullable String pageToken) = 13;

    // PAGES: dispatch interactive user action on current page.
    @nullable ActionResultDto dispatchAction(String actionId, @nullable String paramsJson) = 14;

    // DSP: negotiate shared-memory audio processing session.
    @nullable DspSessionDescriptorDto openDspSession(in DspSessionOfferDto offer) = 15;

    // RECEIVER: open a remote-control cast target receiver session.
    @nullable IPluginReceiverSessionV1 openReceiverSession(in IHostReceiverControllerV1 controller) = 16;

    // OUTPUT_RENDERER: pause remote playback.
    boolean pauseRenderer(String rendererId) = 17;

    // OUTPUT_RENDERER: resume remote playback.
    boolean resumeRenderer(String rendererId) = 18;

    // OUTPUT_RENDERER: seek remote playback to positionMs.
    boolean seekRenderer(String rendererId, long positionMs) = 19;

    // OUTPUT_RENDERER: set remote target volume (0.0 to 1.0).
    boolean setRendererVolume(String rendererId, float volumeFraction) = 20;

    // OUTPUT_RENDERER: stop remote playback session.
    void stopRenderer(String rendererId) = 21;
}
```

---

## 2. `IPluginHostCallbackV1`

Host-provided services passed to the plugin via `registerHostCallback()`.

```aidl
package com.bitperfect.player.sdk.plugin;

import com.bitperfect.player.sdk.plugin.dto.PlaybackTrackDto;
import com.bitperfect.player.sdk.plugin.dto.LibraryPlaylistDto;
import com.bitperfect.player.sdk.plugin.IPluginHttpHandlerV1;

interface IPluginHostCallbackV1 {
    // Extend modes for extendPlayback()
    const int EXTEND_AFTER = 0;   // Insert after the LAST occurrence of anchorTrackId
    const int EXTEND_BEFORE = 1;  // Insert before the FIRST occurrence of anchorTrackId
    const int EXTEND_SCATTER = 2; // Insert at random positions after current playing track

    // @deprecated Plugins must manage credentials locally using plugin-owned EncryptedSharedPreferences.
    @nullable String getCredential(String key) = 1;

    // @deprecated Plugins must manage credentials locally using plugin-owned EncryptedSharedPreferences.
    void putCredential(String key, String value) = 2;

    // Fire-and-forget event notification sent from plugin to host (e.g. renderer discovery updates).
    oneway void postEvent(String eventJson) = 3;

    // @deprecated Plugins must manage credentials locally using plugin-owned EncryptedSharedPreferences.
    void clearCredentials() = 4;

    // Retrieve recent playback history snapshots owned by this plugin.
    List<PlaybackTrackDto> getRecentPlayback(int limit) = 5;

    // Replace the host playback queue with a plugin-owned track list and start playback.
    boolean requestPlayback(in List<PlaybackTrackDto> tracks, int startIndex) = 6;

    // Query user playlists in host library where plugin tracks can be added.
    List<LibraryPlaylistDto> getLibraryPlaylists() = 7;

    // Append plugin tracks to a host library playlist; blank playlistId creates new playlist with newTitle.
    @nullable String addToLibraryPlaylist(String playlistId, String newTitle, in List<PlaybackTrackDto> tracks) = 8;

    // NETWORK_GATEWAY: register an mDNS-advertised HTTP service endpoint dispatched through host.
    int registerNetworkService(
        String serviceType,
        String serviceName,
        in List<String> paths,
        in Map<String, String> txtRecords,
        in IPluginHttpHandlerV1 handler
    ) = 9;

    // NETWORK_GATEWAY: unregister an active network service endpoint.
    void unregisterNetworkService(int registrationId) = 10;

    // Grow current host queue dynamically relative to anchorTrackId.
    // Returns false if anchor was cleared or replaced (signals plugin to cease pagination).
    boolean extendPlayback(in List<PlaybackTrackDto> tracks, String anchorTrackId, int mode) = 11;

    // Return count of unplayed tracks remaining in queue after currently playing track (-1 if empty or external).
    int getRemainingQueueTracks() = 12;
}
```

---

## 3. `IPluginDspSessionV1`

Session interface returned inside `DspSessionDescriptorDto.sessionBinder` when accepting a DSP session.

```aidl
package com.bitperfect.player.sdk.plugin;

import com.bitperfect.player.sdk.plugin.dto.DspStageInfoDto;

interface IPluginDspSessionV1 {
    // Retrieve metadata and stage latency info for the active DSP session.
    DspStageInfoDto getStageInfo() = 1;

    // Reset internal DSP filter states (clear delay lines, ring buffers, and biquad histories).
    void reset() = 2;

    // Close the DSP session and release unmapped buffers.
    void close() = 3;
}
```

---

## 4. `IPluginReceiverSessionV1`

Active remote-control target session returned from `openReceiverSession()`. All methods are non-blocking `oneway`.

```aidl
package com.bitperfect.player.sdk.plugin;

interface IPluginReceiverSessionV1 {
    // Host reports playback state change (0=IDLE, 1=BUFFERING, 2=PLAYING, 3=PAUSED, 4=STOPPED, 5=ERROR).
    oneway void onPlaybackStateChanged(int state);

    // Host reports position progression tick (positionMs and track durationMs).
    oneway void onPositionTick(long positionMs, long durationMs);

    // Host reports active audio output DAC format changes (sampleRate in Hz, bitDepth in bits).
    oneway void onDeviceFormatChanged(int sampleRate, int bitDepth);

    // Host reports buffer status updates (underflow/underrun conditions).
    oneway void onBufferStateChanged(int bufferState);

    // Host commands immediate teardown of the active receiver session.
    oneway void onCloseSession();

    // Host reports active track transition with track ID and duration.
    oneway void onTrackTransition(String trackId, long durationMs);

    // Host reports natural completion of the playback queue.
    oneway void onQueueEnded();

    // Host reports active hardware/software volume fraction (0.0..1.0) and mute status.
    oneway void onDeviceVolumeChanged(float volumeFraction, boolean isMuted);
}

---

## 5. `IHostReceiverControllerV1`

Host-provided controller interface passed into `openReceiverSession()`. All methods are non-blocking `oneway`.

```aidl
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
    oneway void prepareQueue(in List<TrackRefDto> tracks, int activeIndex, long startPositionMs, boolean autoplay);
    oneway void setDeviceMuted(boolean isMuted);
}
```
