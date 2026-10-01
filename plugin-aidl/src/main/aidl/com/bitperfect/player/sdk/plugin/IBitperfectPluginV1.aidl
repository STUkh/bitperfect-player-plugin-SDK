// IBitperfectPluginV1 — Bitperfect Player out-of-process plugin contract, API level 6.
//
// STABILITY: transaction ids are explicit and APPEND-ONLY. A method is never
// removed, reordered, or re-signed; new methods append with the next id and a
// host/plugin must gate calls on getApiVersion().
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
    // API level this plugin was compiled against; host rejects out-of-range versions.
    int getApiVersion() = 1;

    // Bitmask of Capability bits (SOURCE=1, METADATA=2, OUTPUT_RENDERER=4, ...).
    int getCapabilities() = 2;

    // SOURCE: resolve a playable stream for a plugin-scoped track.
    @nullable StreamDescriptorDto resolveStream(in TrackRefDto track, int maxBitDepth, int maxSampleRate) = 3;

    // SOURCE: browse a folder level; null folderId means root.
    @nullable BrowseResultDto browse(@nullable String folderId, @nullable String pageToken) = 4;

    // SOURCE: free-text search.
    @nullable SearchResultDto search(String query, @nullable String pageToken) = 5;

    // SOURCE: album detail with track list.
    @nullable AlbumDto getAlbum(String albumId) = 6;

    // SOURCE: map a shared URL to a track ref, null when not recognised.
    @nullable TrackRefDto parseUrl(String url) = 7;

    // OUTPUT_RENDERER: renderers currently visible to this plugin.
    List<RendererInfoDto> listRenderers() = 8;

    // OUTPUT_RENDERER: start playback of hostMediaUrl on a renderer.
    boolean playOnRenderer(String rendererId, in TrackRefDto track, String hostMediaUrl) = 9;

    // OUTPUT_RENDERER: pre-load the next track for gapless transition.
    boolean enqueueNextOnRenderer(String rendererId, in TrackRefDto track, String hostMediaUrl) = 10;

    void registerHostCallback(in IPluginHostCallbackV1 callback) = 11;
    void unregisterHostCallback(in IPluginHostCallbackV1 callback) = 12;

    // PAGES: render a declarative page for routeId; null when the plugin has no such route.
    @nullable PageDescriptorDto renderPage(String routeId, @nullable String paramsJson, @nullable String pageToken) = 13;

    // PAGES: dispatch a page action by id.
    @nullable ActionResultDto dispatchAction(String actionId, @nullable String paramsJson) = 14;

    // DSP: open a shared-memory DSP session.
    @nullable DspSessionDescriptorDto openDspSession(in DspSessionOfferDto offer) = 15;

    // RECEIVER: open a remote-control receiver session with the host.
    @nullable IPluginReceiverSessionV1 openReceiverSession(in IHostReceiverControllerV1 controller) = 16;

    // OUTPUT_RENDERER: transport controls for active renderer.
    boolean pauseRenderer(String rendererId) = 17;
    boolean resumeRenderer(String rendererId) = 18;
    boolean seekRenderer(String rendererId, long positionMs) = 19;
    boolean setRendererVolume(String rendererId, float volumeFraction) = 20;
    void stopRenderer(String rendererId) = 21;
}
