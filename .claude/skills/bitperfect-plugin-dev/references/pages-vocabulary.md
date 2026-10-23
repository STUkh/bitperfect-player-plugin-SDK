# Pages API v1 SDUI Vocabulary & Schema Reference

Authoritative Server-Driven UI (Pages API v1) specification for Bitperfect Player plugins declaring `CAPABILITY_PAGES` (bit 256).

---

## 1. Root Page Schema (`PluginPage`)

```json
{
  "v": 1,
  "title": "Station Catalog",
  "subtitle": "Curated Lossless Streams",
  "layout": "DETAIL",
  "showRootNavigation": true,
  "accentColor": "#FF8800",
  "contentType": "ALBUM",
  "hero": {
    "title": "Featured Release",
    "subtitle": "Studio Master 192kHz/24bit",
    "meta": "Released Today",
    "image": { "url": "https://example.com/art.jpg", "shape": "SQUARE" },
    "actions": [
      {
        "id": "play_hero",
        "label": "Play Now",
        "icon": "play",
        "style": "PRIMARY",
        "action": { "type": "play", "tracks": [ ... ] }
      }
    ]
  },
  "tabs": {
    "selectedId": "featured",
    "tabs": [
      { "id": "featured", "label": "Featured", "params": { "tab": "featured" } },
      { "id": "genres", "label": "Genres", "params": { "tab": "genres" } }
    ]
  },
  "search": {
    "placeholder": "Search catalog...",
    "routeId": "catalog",
    "paramKey": "q",
    "minChars": 2
  },
  "toolbar": [
    {
      "id": "refresh",
      "label": "Refresh",
      "icon": "refresh",
      "style": "SECONDARY",
      "action": { "type": "dispatch", "actionId": "reload" }
    }
  ],
  "sections": [ ... ],
  "empty": {
    "title": "No tracks found",
    "message": "Try adjusting your search filters.",
    "action": { "id": "reset", "label": "Reset", "action": { "type": "navigate", "routeId": "catalog" } }
  },
  "auth": {
    "message": "Sign in to access your library",
    "activity": "com.example.plugin.LoginActivity",
    "label": "Sign In"
  }
}
```

### Page Fields

| Field | Type | Description |
|---|---|---|
| `v` | `Int` | Protocol version. Must be `1`. |
| `title` | `String` | Screen title (max 200 chars). |
| `subtitle` | `String?` | Optional subtitle (max 200 chars). |
| `layout` | `String` | Screen layout: `"DETAIL"` (back-navigable child page) or `"ROOT"` (top-level hub). |
| `showRootNavigation` | `Boolean` | When `true`, displays root navigation affordances. Default `true`. |
| `accentColor` | `String?` | Brand accent color formatted as strict hex `"#RRGGBB"`. |
| `contentType` | `String?` | `"TRACK"`, `"ALBUM"`, `"ARTIST"`, or `"PLAYLIST"`. |
| `hero` | `PluginHero?` | Header hero banner with title, meta, artwork, and action buttons. |
| `tabs` | `PluginTabStrip?` | Tab bar mounted beneath toolbar (max 8 tabs). |
| `search` | `PluginSearchBox?` | Inline search input field triggering page re-render. |
| `toolbar` | `List<PluginButton>` | Top-bar action icons/buttons (max 4 buttons). |
| `sections` | `List<PluginSection>` | List of content sections (max 40 sections). |
| `empty` | `PluginEmptyState?` | Fallback view rendered when all sections are empty. |
| `auth` | `PluginAuthRequired?` | Overrides body with sign-in prompt when authentication is required. |

---

## 2. Section Kinds (`kind`)

| Kind | Description | Host Behavior & Constraints |
|---|---|---|
| `LIST` | Vertical track or item list | Displays song rows with track numbers, duration, format badges (`HI_RES`, `LOSSLESS`, `DSD`, `LOSSY`), and context menus. Supports `nextPageToken` for infinite scroll. |
| `GRID` | 2-column adaptive grid | Vertical grid of album or playlist cards with square artwork. |
| `SHELF` | Horizontal scroll carousel | Compact horizontal carousel of releases (160–172 dp item width). |
| `CHIPS` | Category filter chips | Horizontal chip selector; active chip matches `selectedId`. Tapping dispatches action or navigates. |
| `HERO_CAROUSEL` | Hero promotion carousel | Large full-width artwork carousel for featured promotions. |
| `QUICK_PLAY` | 3×3 quick play grid | Grid widget for instantly playing top favorite playlists/stations (capped at 27 items). |
| `RADIO` | Single-choice radio list | Radio list with radio buttons; selection dispatches action with selected item ID. |
| `BUTTONS` | Action buttons list | Vertical rows with title, subtitle, optional icon, and navigation chevron. |
| `SWITCH` | Boolean toggle row | Toggle switch based on `BpSwitch`. See 120 FPS Latency Shield. |
| `SLIDER` | Numeric value slider | Continuous or stepped slider based on `BpSlider`. See 120 FPS Latency Shield. |
| `KEY_VALUE` | Technical telemetry table | Two-column property table (`title` left, `meta` right). Ideal for DSP and stream telemetry. |
| `TEXT` | Multi-line text block | Descriptions, release notes, license texts (up to 4000 chars per item title). |

### Section Schema (`PluginSection`)

```json
{
  "id": "recent_releases",
  "kind": "SHELF",
  "header": {
    "title": "New Releases",
    "action": { "type": "navigate", "routeId": "releases_all" }
  },
  "items": [ ... ],
  "nextPageToken": "cursor_page_2",
  "selectedId": null,
  "compact": false,
  "loading": false,
  "empty": null,
  "error": null
}
```

---

## 3. Item Schema (`PluginItem`)

```json
{
  "id": "trk_101",
  "title": "Symphony No. 5 in C minor, Op. 67",
  "subtitle": "Ludwig van Beethoven",
  "image": { "url": "https://example.com/cover.jpg", "shape": "SQUARE" },
  "badge": "HI_RES",
  "trackNumber": 1,
  "discNumber": 1,
  "year": "1808",
  "count": 4,
  "meta": "24-bit / 192 kHz FLAC",
  "favourite": true,
  "contentType": "TRACK",
  "formatTier": "HI_RES",
  "enabled": true,
  "value": 0.75,
  "minValue": 0.0,
  "maxValue": 1.0,
  "step": 0.05,
  "track": {
    "trackId": "trk_101",
    "albumId": "alb_50",
    "title": "Symphony No. 5 in C minor, Op. 67",
    "artist": "Ludwig van Beethoven",
    "album": "Beethoven: The Symphonies",
    "durationMs": 442000,
    "artworkUrl": "https://example.com/cover.jpg",
    "quality": "192kHz/24bit",
    "formatTier": "HI_RES",
    "discNumber": 1
  },
  "action": {
    "type": "play",
    "tracks": [ ... ],
    "startIndex": 0
  },
  "menu": [
    {
      "label": "Add to Playlist",
      "icon": "playlist_add",
      "role": "addToPlaylist",
      "action": { "type": "dispatch", "actionId": "add_to_playlist", "params": { "trackId": "trk_101" } }
    },
    {
      "label": "Toggle Favorite",
      "icon": "favorite",
      "role": "favourite",
      "action": { "type": "dispatch", "actionId": "toggle_fav", "params": { "trackId": "trk_101" } }
    }
  ]
}
```

### Action Types (`action.type`)

- `"play"`: Replaces host playback queue with `tracks` list, starting at `startIndex`.
- `"enqueue"`: Appends `tracks` to the end of the host queue.
- `"playNext"`: Inserts `tracks` immediately following the currently playing track.
- `"navigate"`: Navigates to `routeId` with `params` map.
- `"dispatch"`: Calls plugin `onDispatchAction(actionId, paramsJson)`. Supports `confirm` dialogs and `prompt` text inputs.
- `"openUrl"`: Launches external browser for secure HTTPS URL (`url` must start with `https://`).
- `"openActivity"`: Launches plugin's own exported Activity (`component`).

### Menu Roles & Format Tiers

- **Format Tiers (`formatTier`)**: `"HI_RES"`, `"LOSSLESS"`, `"DSD"`, `"LOSSY"`.
- **Menu Roles (`role`)**: `"favourite"`, `"addToPlaylist"`, `"remove"`, `"play"`, `"shuffle"`, `"more"`.
- **Image Shapes (`shape`)**: `"SQUARE"`, `"CIRCLE"`.

### Host-native Add to playlist (`role: "addToPlaylist"`)

Source plugins use the host playlist sheet. No AIDL or API level change. Plain `onDispatchAction` ids.

| Action | Params | Reply |
|---|---|---|
| `playlist.list` | none | `KIND_NONE`, json `{"playlists":[{"id","title","count"}]}` |
| `playlist.add` | `playlistId`, `trackId` | `KIND_TOAST` |
| `playlist.create` | `input` (name), optional `trackId` | `KIND_TOAST` |

- `playlist.list` returns only playlists the user can edit. `count` is optional. An empty list is valid.
- Contract code is in `plugin-pages`, package `com.bitperfect.player.core.plugin.page`:
  - `ServicePlaylistActions`: action ids `LIST`, `ADD`, `CREATE`; param keys `PARAM_PLAYLIST_ID`, `PARAM_TRACK_ID`, `PARAM_NAME`.
  - `ServicePlaylist(id, title, count?)`.
  - `ServicePlaylistCodec`: encodes and decodes the `playlist.list` body.
  - `servicePlaylistChooseOptions(playlists, trackId)`: builds a Choose list for a host without the native sheet.
- `plugin-runtime` holds `List<ServicePlaylist>.toListResult()` and `ServicePlaylistCache`.
- On error, reply `KIND_TOAST` with a message, or `KIND_AUTH_REQUIRED` when signed out. The host shows the message and does not fall back to local playlists.
- The host sends `playlist.list` for a track item with `role: "addToPlaylist"`. If the plugin answers, the host shows its sheet and calls `playlist.add` or `playlist.create`. If the plugin does not answer (`KIND_NONE`, empty json), the host runs the entry's own `action`.
- For a plugin track in Now Playing, queue, or history, the host uses the same sheet when `playlist.list` answers. Otherwise it shows local playlists.
- `ServicePlaylistCache` loads the list once per request (single-flight). The TTL is 10 min (`SERVICE_PLAYLISTS_TTL_MS`). Use `created`, `added`, `renamed`, `removed`, `seed`, and `title` to write changes through to the cache. Do not refetch on add or create. Do not fetch the playlist or the track in `playlist.add`.
- Call `invalidate()` on sign-out. A load that races `invalidate()` is not stored.

---

## 4. Action Results (`PluginActionResult` / `ActionResultDto`)

Returned from `onDispatchAction(actionId, paramsJson)`:

| Kind Constant | Result Class | Wire JSON Payload | Behavior |
|---|---|---|---|
| `"NONE"` | `None` | `{}` | No host UI change. |
| `"REFRESH"` | `Refresh` | `{}` | Re-renders current page via `onRenderPage`. |
| `"NAVIGATE"` | `Navigate` | `{"routeId": "album", "params": {"id": "1"}}` | Pushes route to host navigation stack. |
| `"CHOOSE"` | `Choose` | `{"title": "Select Format", "options": [ ... ]}` | Displays modal bottom sheet with menu options. |
| `"TOAST"` | `Toast` | `{"text": "Downloaded to cache"}` | Displays short notification toast. |
| `"AUTH_REQUIRED"` | `AuthRequired` | `{"message": "Sign in required", "activity": "com.example.Login"}` | Prompts user to authenticate. |

---

## 5. Parser Limits (`PluginPageLimits`)

Host enforces strict defensive boundaries. Descriptors exceeding limits are truncated or rejected:

| Constraint | Limit | Effect When Exceeded |
|---|---|---|
| Max JSON payload (`MAX_JSON_BYTES`) | 512 KiB (524,288 bytes) | Entire page rejected with parse error. |
| Max JSON nesting depth (`MAX_DEPTH`) | 12 levels | Parser rejects payload to prevent stack overflow. |
| Max sections per page (`MAX_SECTIONS`) | 40 sections | Truncated to first 40 sections. |
| Max items per section (`MAX_ITEMS_PER_SECTION`) | 500 items | Truncated to first 500 items. |
| Max tracks per action (`MAX_TRACKS_PER_ACTION`) | 500 tracks | Truncated to first 500 tracks. |
| Max quick play items (`MAX_QUICK_PLAY`) | 27 items | Truncated to first 27 items in `QUICK_PLAY` sections. |
| Max text length (`MAX_TEXT`) | 200 characters | Truncated to 200 chars (titles, subtitles, meta). |
| Max long text length (`MAX_TEXT_LONG`) | 4000 characters | Truncated to 4000 chars (for `TEXT` section items). |
| Max URL length (`MAX_URL`) | 2048 characters | Dropped if over limit or non-HTTPS. |
| Max context menu entries (`MAX_MENU`) | 12 entries | Truncated to first 12 entries. |
| Max toolbar actions (`MAX_TOOLBAR`) | 4 actions | Truncated to first 4 buttons. |
| Max tabs (`MAX_TABS`) | 8 tabs | Truncated to first 8 tabs. |
| Max choose dialog options (`MAX_CHOOSE`) | 40 options | Truncated to first 40 options. |
| Max hero action buttons (`MAX_HERO_ACTIONS`) | 3 buttons | Truncated to first 3 buttons. |
| Route ID format | `[A-Za-z0-9._-]{1,64}` | Rejected if invalid characters or length > 64. |
| Host timeouts | 10s render / 15s dispatch | Binder transaction aborted if unresponsive. |

---

## 6. 120 FPS Latency Shield & Interaction Rules

To guarantee stutter-free 120 FPS rendering on high-refresh-rate audiophile Android hardware:

1. **Zero IPC Binder calls during touch drags**:
   - `SLIDER`: The host updates slider thumb position and numeric text readout natively on the UI thread at up to 120 Hz.
   - The plugin receives **zero IPC calls** while the user's finger is moving.
   - Binder call `onDispatchAction` with `{ "value": "<float_string>" }` is dispatched **only on touch release** (`MotionEvent.ACTION_UP`).
2. **Instant Local Switch Toggling**:
   - `SWITCH`: The host toggles the `BpSwitch` switch thumb state immediately upon tap.
   - An asynchronous `onDispatchAction` call is dispatched with `{ "checked": "true" | "false" }`.
   - If the plugin returns `PluginActionResult.None`, the state remains toggled.
3. **Stateless Re-renders**:
   - The host maintains view hierarchy, scroll positions, and backstack. Plugins do not hold host UI handles.
