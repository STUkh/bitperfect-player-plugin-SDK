# Server-Driven UI (Pages API v1) Guide

Bitperfect Player features a Server-Driven UI (SDUI) engine called **Pages API v1**.
When a plugin declares `CAPABILITY_PAGES` (bit `256`), its screens within the player are rendered natively by the host application from declarative JSON descriptors.

---

## 1. Core Principles

* **Native Flat-Dark Rendering**: The host renders UI nodes using its own design tokens, typography, and hardware-accelerated animations.
* **Zero Host Infiltration**: The player never loads external Compose code or invokes `DexClassLoader`.
* **Statelessness**: The plugin returns page descriptors statelessly from `onRenderPage(routeId, paramsJson, pageToken)`. The host manages backstack, scroll position, and mini-player transitions.
* **120 FPS Smoothness (Zero-IPC Latency)**: Dragging sliders and flipping switches is animated natively on the UI thread at up to 120 Hz. IPC Binder calls are sent only upon final user interaction (e.g. finger release).

---

## 2. Page Structure (`PluginPage`)

A page descriptor is represented in Kotlin by `PluginPage` (or raw JSON):

```json
{
  "v": 1,
  "title": "Page Title",
  "subtitle": "Optional Subtitle",
  "layout": "DETAIL",
  "sections": [ ... ],
  "empty": null,
  "toolbar": [ ... ],
  "tabs": null,
  "auth": null
}
```

### Layout Types
* `"DETAIL"`: Standard scrolling view with back button, ideal for catalogs, albums, and settings.
* `"ROOT"`: Top-level view mounted into player navigation.

---

## 3. Supported Section Kinds (`kind`)

| Section Kind | Description | Host Behavior |
|---|---|---|
| `LIST` | Vertical track/item list | Displays song rows with track numbers, duration, format badges (`HI_RES`, `LOSSLESS`), and context menu. |
| `GRID` | 2-column adaptive grid | Vertical grid of album or playlist cards with square artwork. |
| `SHELF` | Horizontal scroll shelf | Compact horizontal carousel of releases (160–172 dp width). |
| `CHIPS` | Category filter chips | Horizontal chip selector; active chip matches `selectedId`. |
| `HERO_CAROUSEL` | Hero promotion carousel | Large full-width artwork carousel for featured content. |
| `QUICK_PLAY` | 3×3 quick play grid | Grid widget for instantly playing top favorite playlists/stations. |
| `RADIO` | Single-choice radio list | Radio list with radio buttons; selection dispatches action. |
| `BUTTONS` | Action buttons list | Vertical rows with title, subtitle, and navigation chevron. |
| `SWITCH` | Boolean toggle row | Toggle switch based on `BpSwitch`. See section below. |
| `SLIDER` | Numeric value slider | Continuous or stepped slider based on `BpSlider`. See section below. |
| `KEY_VALUE` | Telemetry & metadata table | Technical property table (`title` left, `meta` right). |
| `TEXT` | Multi-line text block | Descriptions, release notes, license texts (up to 4000 chars). |

---

## 4. Interactive Components & 120 FPS Performance

### 4.1. Switch (`SWITCH`)
Represents a boolean configuration setting.
* Uses `item.title`, `item.subtitle`, and `item.enabled`.
* When clicked, the host immediately flips the switch visually.
* Dispatches `onDispatchAction` with parameter:
  ```json
  { "checked": "true" } // or "false"
  ```

### 4.2. Slider (`SLIDER`)
Represents continuous or stepped numeric adjustments (e.g. Pre-Amp Gain, Crossfeed, Crossover frequency).
* **Properties**:
  * `item.value`: Current numeric value (Float).
  * `item.minValue`: Minimum bound.
  * `item.maxValue`: Maximum bound.
  * `item.step`: Step increment (e.g. `0.5`).
  * `item.meta`: Textual readout (e.g. `"-1.5 dB"`).
* **120 FPS Latency Shield**:
  * During finger touch/drag, the host tracks motion and updates the `item.meta` readout at up to 120 Hz completely locally in the host process.
  * No Binder calls are generated while dragging.
  * **Only when the user lifts their finger** (`onValueChangeFinished`), the host sends a single `onDispatchAction` call with:
    ```json
    { "value": "-1.5" }
    ```
  * This completely eliminates IPC stutter and ensures buttery smooth animations.

### 4.3. Key-Value Table (`KEY_VALUE`)
Displays technical properties and status telemetry:
* `item.title`: Key name on the left (e.g. `"Audio Format"`).
* `item.meta`: Value text on the right (e.g. `"96kHz / 24-bit FLAC"`).
* `item.subtitle`: Optional explanatory text below the title.

---

## 5. Supported Action Types

Actions define what happens when an item, button, or slider is activated:

* **`play`**: Starts immediate playback of specified tracks.
  ```json
  { "type": "play", "tracks": [ { "trackId": "101", "title": "Song", "artist": "Artist", "durationMs": 240000 } ] }
  ```
* **`enqueue`**: Appends tracks to the end of the current play queue.
* **`playNext`**: Inserts tracks immediately after the currently playing song.
* **`navigate`**: Navigates to another declarative route within the plugin.
  ```json
  { "type": "navigate", "routeId": "album_detail", "params": { "id": "album_99" } }
  ```
* **`dispatch`**: Calls the plugin's `onDispatchAction(actionId, paramsJson)`.
  ```json
  { "type": "dispatch", "actionId": "toggle_favorite", "params": { "trackId": "101" } }
  ```
* **`openActivity`**: Deep-links to an exported plugin Activity (e.g. for OAuth login).
* **`openUrl`**: Opens an external web URL in the system browser.

### Host-native Add to playlist

Source plugins use the host playlist sheet. No AIDL or API level change. Plain `onDispatchAction` ids.

| Action | Params | Reply |
|---|---|---|
| `playlist.list` | none | `KIND_NONE`, json `{"playlists":[{"id","title","count"}]}` |
| `playlist.add` | `playlistId`, `trackId` | `KIND_TOAST` |
| `playlist.create` | `input` (name), optional `trackId` | `KIND_TOAST` |

* `playlist.list` returns only playlists the user can edit. `count` is optional. An empty list is valid.
* On error, reply `KIND_TOAST` with a message, or `KIND_AUTH_REQUIRED` when signed out. The host shows the message and does not fall back to local playlists.
* A track item with `role: "addToPlaylist"` makes the host send `playlist.list`. If the plugin answers, the host shows its sheet and calls `playlist.add` or `playlist.create`. If the plugin does not answer, the host runs the entry's own `action`.
* Now Playing, queue, and history use the same sheet when `playlist.list` answers. Otherwise they show local playlists.
* The contract code is in `plugin-pages` (package `com.bitperfect.player.core.plugin.page`): `ServicePlaylistActions` (action ids and param keys), `ServicePlaylist`, `ServicePlaylistCodec` (the `playlist.list` body), and `servicePlaylistChooseOptions` (a Choose list for a host without the native sheet).
* `plugin-runtime` provides `ServicePlaylistCache`. It loads once per request (single-flight) with a 10 min TTL (`SERVICE_PLAYLISTS_TTL_MS`). Its `created`, `added`, `renamed`, `removed`, `seed`, and `title` functions write changes through to the cache. Do not refetch on add or create. Do not fetch the playlist or the track in `playlist.add`.
* Call `invalidate()` on sign-out. A load that races `invalidate()` is not stored.

### Account scope and staged library sync (`plugin-runtime`, no API change)

Use these for a service that keeps per-user data (cache DB, id sets, playlist cache) and a library of thousands of items.

* `AccountScope(userId, parent)` is a `CoroutineScope` for one signed-in user. Put the user's storage, caches and jobs in a subclass. `close()` releases the storage. `setFill(job)` / `cancelFill()` keep one playback-fill job. `guarded { }` runs a storage access as a child of the scope, so teardown joins it before the storage closes. It returns `null` when the account is gone.
* `AccountScopes(parent, factory, deleteStorage, owner, legacyCleanup, authRequired)` owns the one live scope in `current`. The plugin has ONE storage (one DB file). `owner` (`StorageOwner`; `FileStorageOwner(file)` writes with temp + rename) holds the id of the account that owns it. `open(userId)` reuses the same user and tears down a different one first. When `owner.read() != userId`, `open` calls `deleteStorage()`, then `owner.write(userId)`, then `factory(userId)`; so a crash between the delete and the owner write wipes again on the next open. `require()` throws `authRequired()` (default `AuthRequiredException`). `detach()` clears the reference at once and does the teardown in the background (cancel, join, `close()`, `deleteStorage()`, `owner.write(null)`). `logout(clear)` waits for the teardown and then runs `clear` even when it failed. `legacyCleanup` runs once at start; `legacyPerUserDatabases(names, prefix, keep)` lists old per-user DB files to delete. Use one `AccountScopes` for login, session expiry and logout, so no path leaves the old user's data readable.
* `StagedLibrarySync(pager, store, scope, stallMs, pageSize, cap, onProgress)` copies a remote list (newest first) into a staging table and replaces the visible table in one `swap` transaction. It never clears the visible table first. The cursor (`sync_offset`, `sync_total`, `sync_head_id`) lives in the store, so a cancelled or failed run resumes when the server total and newest id are unchanged. `ensureRunning()` starts one job per account and returns the running job if there is one. There is no total deadline: only a step with no progress for `stallMs` fails the run. Implement `LibraryPager` (`probe()` gives `LibraryHead(total, headId)`; `page(offset, limit)`) and `StagingStore` (meta, staging count, `stage`, `swap`, `isUpToDate`). Render from the visible table or the first network page, and show `onProgress` as "done / total".

---

## 6. Descriptor Constraints (Enforced at Parse)

To prevent resource exhaustion and keep memory footprint low:
* **Max descriptor size**: 512 KiB per page.
* **Max nesting depth**: 12 levels.
* **Max sections per page**: 40 sections.
* **Max items per section**: 500 items (use pagination via `nextPageToken` for larger lists).
* **Max inline tracks per action**: 500 tracks.
* **Timeouts**: one policy, `PluginCallPolicy` (override `callPolicy` in your service): `resolveStream` 20 s, `browse` 60 s. `renderPage` 120 s and `dispatchAction` 60 s are fixed by the contract (`PluginCallLimits`) and cannot be overridden. The host waits the plugin limit plus 10 s. Plugins below API 9 cannot be cancelled, so the host keeps its old limits for them (`renderPage` 10 s, `dispatchAction` 15 s). The host runs at most 4 binder calls per plugin at the same time.
* **Cancellation (API 9)**: the host puts `_bp_request_id` (`PageDescriptorDto.PARAM_REQUEST_ID`) into `paramsJson` and calls `cancelRequest(id)` when the user leaves the page or the call times out. Override `onRenderPageRequest(PageRequest)` / `onDispatchActionRequest(ActionRequest)` and keep work cancellable; the SDK runs them in the service scope and frees the Binder thread on cancel. `PageRequest.params` is already parsed and has no `_bp_*` keys; the request id is in `requestId`. The older `onRenderPage` / `onRenderPageSuspend` and `onDispatchAction` / `onDispatchActionSuspend` hooks still work and get the raw `paramsJson`. For the old hooks, `String?.toParamsMap()` gives the same stripped map.
* **Paging helpers**: `OffsetPageToken.parse(token, max)` and `OffsetPageToken.nextPageToken(offset, count, total)` for plain offset tokens; `PageEvents.invalidate(routes)` / `PageEvents.invalidateAll()` build the `pages.invalidate` event for `postEvent`.
* **API levels**: use the named constants in `ApiLevel` (`CURRENT`, `PAGES`, `RECEIVER`, `CANCEL_REQUEST`). Renderer and DSP bases report `ApiLevel.RENDERER_DEFAULT` (7) because their `cancelRequest` does nothing.
