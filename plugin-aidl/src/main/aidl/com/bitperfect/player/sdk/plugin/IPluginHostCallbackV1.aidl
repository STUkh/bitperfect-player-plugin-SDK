// IPluginHostCallbackV1 — host services exposed to an authenticated plugin binding.
package com.bitperfect.player.sdk.plugin;
import com.bitperfect.player.sdk.plugin.dto.PlaybackTrackDto;
import com.bitperfect.player.sdk.plugin.dto.LibraryPlaylistDto;
import com.bitperfect.player.sdk.plugin.IPluginHttpHandlerV1;

interface IPluginHostCallbackV1 {
    // Grow the queue that requestPlayback started: insert tracks relative to this plugin's
    // anchorTrackId already in the Main queue. false = anchor gone (user replaced the queue),
    // the plugin must stop feeding pages.
    const int EXTEND_AFTER = 0;   // after the LAST occurrence of anchorTrackId
    const int EXTEND_BEFORE = 1;  // before the FIRST occurrence of anchorTrackId
    const int EXTEND_SCATTER = 2; // uniformly random positions after the current track; anchor ignored

    // @deprecated Plugins must manage their own credentials locally using plugin-owned secure storage (e.g. EncryptedSharedPreferences).
    // Retained only for ABI backward compatibility; do not use in new plugins.
    @nullable String getCredential(String key) = 1;

    // @deprecated Plugins must manage their own credentials locally using plugin-owned secure storage.
    // Retained only for ABI backward compatibility; do not use in new plugins.
    void putCredential(String key, String value) = 2;
    // Fire-and-forget JSON event to the host (renderer discovery, auth state, ...).
    oneway void postEvent(String eventJson) = 3;

    // @deprecated Plugins must manage their own credentials locally using plugin-owned secure storage.
    // Retained only for ABI backward compatibility; do not use in new plugins.
    void clearCredentials() = 4;

    // Return genuine recent-listen snapshots owned by this plugin.
    List<PlaybackTrackDto> getRecentPlayback(int limit) = 5;

    // Submit a finite lazy-resolution queue owned by this plugin.
    boolean requestPlayback(in List<PlaybackTrackDto> tracks, int startIndex) = 6;

    // Playlists in the host's library the user can add plugin tracks to.
    List<LibraryPlaylistDto> getLibraryPlaylists() = 7;

    // Append this plugin's tracks to a host playlist; a blank playlistId creates newTitle.
    @nullable String addToLibraryPlaylist(String playlistId, String newTitle, in List<PlaybackTrackDto> tracks) = 8;

    // NETWORK_GATEWAY: register a generic network endpoint (mDNS + HTTP route dispatch).
    int registerNetworkService(
        String serviceType,
        String serviceName,
        in List<String> paths,
        in Map<String, String> txtRecords,
        in IPluginHttpHandlerV1 handler
    ) = 9;

    // NETWORK_GATEWAY: unregister a network endpoint.
    void unregisterNetworkService(int registrationId) = 10;

    // Grow the Main queue with this plugin's next page — see EXTEND_* above.
    boolean extendPlayback(in List<PlaybackTrackDto> tracks, String anchorTrackId, int mode) = 11;

    // Returns the number of unplayed tracks remaining in the queue after the current track.
    // Negative (-1) when the queue is not playing from this plugin or is empty.
    int getRemainingQueueTracks() = 12;
}
