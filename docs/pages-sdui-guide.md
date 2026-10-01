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

---

## 6. Descriptor Constraints (Enforced at Parse)

To prevent resource exhaustion and keep memory footprint low:
* **Max descriptor size**: 512 KiB per page.
* **Max nesting depth**: 12 levels.
* **Max sections per page**: 40 sections.
* **Max items per section**: 500 items (use pagination via `nextPageToken` for larger lists).
* **Max inline tracks per action**: 500 tracks.
* **Timeouts**: `onRenderPage` has a 10-second timeout; `onDispatchAction` has a 15-second timeout.
