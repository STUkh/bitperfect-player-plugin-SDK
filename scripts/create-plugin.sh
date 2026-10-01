#!/usr/bin/env bash
# ==============================================================================
# create-plugin.sh - Scaffold a new Bitperfect Player Plugin Module
# ==============================================================================
# Generates a production-grade Bitperfect Player Plugin module with:
#   - build.gradle.kts (configured for API level 6 and AGP 9)
#   - proguard-rules.pro (retaining plugin lifecycle and DTOs)
#   - src/main/AndroidManifest.xml (conforming to plugin spec)
#   - src/main/res/values/strings.xml
#   - Kotlin Service implementing BitperfectSourcePlugin, BitperfectDspPlugin,
#     or BitperfectRendererPlugin with proper imports and stub implementation
# ==============================================================================

set -euo pipefail

# ANSI color codes
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[0;33m'
    BLUE='\033[0;34m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    BOLD=''
    NC=''
fi

usage() {
    cat <<EOF
${BOLD}Bitperfect Player Plugin Scaffolder${NC}

${BOLD}USAGE:${NC}
    $(basename "$0") [OPTIONS]

${BOLD}OPTIONS:${NC}
    --id <package.name>        Plugin applicationId / package (e.g. com.example.myplugin)
    --name <PluginName>        Plugin name in PascalCase (e.g. TidalSource, TubeDsp)
    --type <source|dsp|renderer> Plugin type (default: source)
    --out <target-dir>         Output directory path
    -h, --help                 Show this help message

${BOLD}EXAMPLES:${NC}
    # Non-interactive generation:
    $(basename "$0") \\
        --id com.example.bitperfect.plugin.flacradio \\
        --name FlacRadioPlugin \\
        --type source \\
        --out sample-flac-radio

    # Interactive prompts:
    $(basename "$0")
EOF
}

PLUGIN_ID=""
PLUGIN_NAME=""
PLUGIN_TYPE=""
TARGET_DIR=""

# Parse command line flags
while [ $# -gt 0 ]; do
    case "$1" in
        --id)
            PLUGIN_ID="${2:-}"
            shift 2
            ;;
        --name)
            PLUGIN_NAME="${2:-}"
            shift 2
            ;;
        --type)
            PLUGIN_TYPE="${2:-}"
            shift 2
            ;;
        --out)
            TARGET_DIR="${2:-}"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo -e "${RED}Error: Unknown argument '$1'${NC}" >&2
            usage >&2
            exit 1
            ;;
    esac
done

# If running interactively and arguments are missing, prompt user
is_interactive() {
    [ -t 0 ] && [ -t 1 ]
}

if [ -z "$PLUGIN_NAME" ]; then
    if is_interactive; then
        read -rp "Enter Plugin Name (PascalCase, e.g. TidalStreamPlugin): " PLUGIN_NAME
    else
        echo -e "${RED}Error: --name is required in non-interactive mode.${NC}" >&2
        exit 1
    fi
fi

# Clean and validate PLUGIN_NAME
PLUGIN_NAME="$(echo "$PLUGIN_NAME" | tr -d '[:space:]')"
if ! [[ "$PLUGIN_NAME" =~ ^[A-Z][a-zA-Z0-9_]*$ ]]; then
    echo -e "${RED}Error: Plugin name '$PLUGIN_NAME' must start with an uppercase letter and contain only alphanumeric characters.${NC}" >&2
    exit 1
fi

# Derive slug (e.g. TidalStreamPlugin -> tidal_stream)
SLUG=$(echo "$PLUGIN_NAME" | sed -E 's/([A-Z])/_\1/g' | tr '[:upper:]' '[:lower:]' | sed -E 's/^_//; s/_plugin$//')

if [ -z "$PLUGIN_ID" ]; then
    DEFAULT_ID="com.example.bitperfect.plugin.$SLUG"
    if is_interactive; then
        read -rp "Enter Application ID / Package [$DEFAULT_ID]: " INPUT_ID
        PLUGIN_ID="${INPUT_ID:-$DEFAULT_ID}"
    else
        echo -e "${RED}Error: --id is required in non-interactive mode.${NC}" >&2
        exit 1
    fi
fi

# Validate PLUGIN_ID
if ! [[ "$PLUGIN_ID" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ ]]; then
    echo -e "${RED}Error: Invalid package/applicationId format: '$PLUGIN_ID'${NC}" >&2
    exit 1
fi

if [ -z "$PLUGIN_TYPE" ]; then
    if is_interactive; then
        echo "Select plugin type:"
        echo "  1) source   - Audio streaming / browsing / cloud provider"
        echo "  2) dsp      - Real-time zero-copy DSP audio processing"
        echo "  3) renderer - Remote playback / network cast target"
        read -rp "Choice [1-3] (default: 1): " TYPE_CHOICE
        case "${TYPE_CHOICE:-1}" in
            1|source) PLUGIN_TYPE="source" ;;
            2|dsp) PLUGIN_TYPE="dsp" ;;
            3|renderer) PLUGIN_TYPE="renderer" ;;
            *)
                echo -e "${YELLOW}Invalid choice, defaulting to 'source'.${NC}"
                PLUGIN_TYPE="source"
                ;;
        esac
    else
        PLUGIN_TYPE="source"
    fi
fi

# Normalize type to lowercase
PLUGIN_TYPE="$(echo "$PLUGIN_TYPE" | tr '[:upper:]' '[:lower:]')"
case "$PLUGIN_TYPE" in
    source|dsp|renderer) ;;
    *)
        echo -e "${RED}Error: Invalid --type '$PLUGIN_TYPE'. Must be 'source', 'dsp', or 'renderer'.${NC}" >&2
        exit 1
        ;;
esac

if [ -z "$TARGET_DIR" ]; then
    DEFAULT_DIR="plugin-$SLUG"
    if is_interactive; then
        read -rp "Enter target directory [$DEFAULT_DIR]: " INPUT_DIR
        TARGET_DIR="${INPUT_DIR:-$DEFAULT_DIR}"
    else
        TARGET_DIR="$DEFAULT_DIR"
    fi
fi

# Service Class Name
if [[ "$PLUGIN_NAME" == *Service ]] || [[ "$PLUGIN_NAME" == *Plugin ]]; then
    SERVICE_CLASS="$PLUGIN_NAME"
else
    SERVICE_CLASS="${PLUGIN_NAME}Service"
fi

echo -e "${BOLD}Scaffolding Bitperfect Player Plugin:${NC}"
echo -e "  Name:         ${BLUE}$PLUGIN_NAME${NC}"
echo -e "  Type:         ${BLUE}$PLUGIN_TYPE${NC}"
echo -e "  Package / ID: ${BLUE}$PLUGIN_ID${NC}"
echo -e "  Service:      ${BLUE}$SERVICE_CLASS${NC}"
echo -e "  Target Dir:   ${BLUE}$TARGET_DIR${NC}"
echo ""

# Create directories
PKG_PATH="$(echo "$PLUGIN_ID" | tr '.' '/')"
mkdir -p "$TARGET_DIR/src/main/java/$PKG_PATH"
mkdir -p "$TARGET_DIR/src/main/res/values"
mkdir -p "$TARGET_DIR/src/test/java/$PKG_PATH"

# 1. Generate build.gradle.kts
cat <<EOF > "$TARGET_DIR/build.gradle.kts"
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "$PLUGIN_ID"
    compileSdk = 37

    defaultConfig {
        applicationId = "$PLUGIN_ID"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Bitperfect Player Plugin SDK dependencies
    // Automatic fallback: links project modules if inside SDK repo, or published Maven artifacts
    val isSdkRepo = rootProject.findProject(":plugin-runtime") != null
    if (isSdkRepo) {
        implementation(project(":plugin-runtime"))
        implementation(project(":plugin-aidl"))
        implementation(project(":plugin-pages"))
    } else {
        implementation("com.bitperfect.player:plugin-runtime:6.0.0")
        implementation("com.bitperfect.player:plugin-aidl:6.0.0")
        implementation("com.bitperfect.player:plugin-pages:6.0.0")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.1")
}
EOF

# 2. Generate proguard-rules.pro
cat <<EOF > "$TARGET_DIR/proguard-rules.pro"
# Retain exported Plugin Service and SDK entry points
-keep class * extends com.bitperfect.player.sdk.plugin.runtime.BitperfectSourcePlugin { <init>(); }
-keep class * extends com.bitperfect.player.sdk.plugin.runtime.BitperfectRendererPlugin { <init>(); }
-keep class * extends com.bitperfect.player.sdk.plugin.runtime.BitperfectDspPlugin { <init>(); }

# Keep DTO classes and Kotlinx serialization models
-keep class com.bitperfect.player.sdk.plugin.dto.** { *; }
-keep class com.bitperfect.player.core.plugin.page.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
EOF

# 3. Generate res/values/strings.xml
cat <<EOF > "$TARGET_DIR/src/main/res/values/strings.xml"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">$PLUGIN_NAME</string>
    <string name="plugin_label">$PLUGIN_NAME</string>
</resources>
EOF

# Type-specific configurations
case "$PLUGIN_TYPE" in
    source)
        CAPABILITIES="265" # SOURCE (1) | UI_TABS (8) | PAGES (256)
        NAV_TARGET="sidebar"
        NAV_ROUTE="catalog"
        NAV_ORDER="10"
        SETTINGS_ROUTE="settings"
        ;;
    dsp)
        CAPABILITIES="768" # DSP (512) | PAGES (256)
        NAV_TARGET="settings"
        NAV_ROUTE="dsp_settings"
        NAV_ORDER="20"
        SETTINGS_ROUTE="dsp_settings"
        ;;
    renderer)
        CAPABILITIES="4" # OUTPUT_RENDERER (4)
        NAV_TARGET="settings"
        NAV_ROUTE="renderer"
        NAV_ORDER="30"
        SETTINGS_ROUTE="renderer"
        ;;
esac

# 4. Generate AndroidManifest.xml
cat <<EOF > "$TARGET_DIR/src/main/AndroidManifest.xml"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="false"
        android:icon="@android:drawable/ic_media_play"
        android:label="@string/app_name">

        <!-- Exported Bitperfect Player Plugin Service -->
        <service
            android:name=".$SERVICE_CLASS"
            android:exported="true">
            <intent-filter>
                <action android:name="com.bitperfect.plugin.BIND_PLUGIN" />
            </intent-filter>

            <!-- 1. Unique plugin identifier: MUST strictly match the APK applicationId -->
            <meta-data
                android:name="com.bitperfect.plugin.PLUGIN_ID"
                android:value="$PLUGIN_ID" />

            <!-- 2. Capabilities bitmask: SOURCE, DSP, or RENDERER with PAGES (256) -->
            <meta-data
                android:name="com.bitperfect.plugin.CAPABILITIES"
                android:value="$CAPABILITIES" />

            <!-- 3. Minimum supported host API level (6 for latest features) -->
            <meta-data
                android:name="com.bitperfect.plugin.MIN_HOST_API"
                android:value="6" />

            <!-- 4. Navigation integration into Bitperfect Player host UI -->
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_EXTENSION_ID"
                android:value="${SLUG}_nav" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_TARGET"
                android:value="$NAV_TARGET" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_LABEL"
                android:value="@string/plugin_label" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_ROUTE"
                android:value="$NAV_ROUTE" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_ORDER"
                android:value="$NAV_ORDER" />

            <!-- 5. Settings screen route (Pages API v1) -->
            <meta-data
                android:name="com.bitperfect.plugin.SETTINGS_ROUTE"
                android:value="$SETTINGS_ROUTE" />
        </service>

    </application>
</manifest>
EOF

# 5. Generate Kotlin Service class based on type
KOTLIN_FILE="$TARGET_DIR/src/main/java/$PKG_PATH/$SERVICE_CLASS.kt"

if [ "$PLUGIN_TYPE" = "source" ]; then
cat <<EOF > "$KOTLIN_FILE"
package $PLUGIN_ID

import com.bitperfect.player.core.plugin.page.*
import com.bitperfect.player.sdk.plugin.dto.*
import com.bitperfect.player.sdk.plugin.runtime.BitperfectSourcePlugin
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk
import kotlinx.serialization.json.Json

/**
 * Bitperfect Audio Source Plugin: $PLUGIN_NAME
 *
 * Demonstrates:
 * 1. Audio stream resolution: Returning direct bit-perfect stream descriptors (HTTP, SMB, WebDAV)
 * 2. Browsing and search: Discovering hierarchy, folders, and audio tracks
 * 3. Server-Driven UI: Rendering catalog and settings pages using Pages API v1
 */
class $SERVICE_CLASS : BitperfectSourcePlugin() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun capabilities(): Int =
        PluginSdk.CAPABILITY_SOURCE or PluginSdk.CAPABILITY_UI_TABS or PluginSdk.CAPABILITY_PAGES

    /**
     * Resolves a direct audio stream descriptor for the host player.
     * The host Bitperfect Player handles network buffering, bit-perfect decoding, and hardware DAC output.
     */
    override fun onResolveStream(
        track: TrackRefDto,
        maxBitDepth: Int,
        maxSampleRate: Int,
    ): StreamDescriptorDto? {
        // Return a direct media stream descriptor
        return null
    }

    /**
     * Browses catalog folders or categories.
     */
    override fun onBrowse(folderId: String?, pageToken: String?): BrowseResultDto? {
        return BrowseResultDto(
            items = emptyList(),
            nextPageToken = null,
        )
    }

    /**
     * Searches audio tracks, albums, or artists.
     */
    override fun onSearch(query: String, pageToken: String?): SearchResultDto? {
        return SearchResultDto(
            tracks = emptyList(),
            nextPageToken = null,
        )
    }

    /**
     * Renders Server-Driven UI pages for the host app.
     */
    override fun onRenderPage(
        routeId: String,
        paramsJson: String?,
        pageToken: String?,
    ): PageDescriptorDto? {
        val page = PluginPage(
            title = "$PLUGIN_NAME",
            subtitle = "Bitperfect Source Plugin",
            sections = listOf(
                PluginSection(
                    id = "welcome_sec",
                    kind = "TEXT",
                    header = PluginSectionHeader(title = "Welcome to $PLUGIN_NAME"),
                    items = listOf(
                        PluginItem(
                            id = "intro",
                            title = "$PLUGIN_NAME is Active",
                            subtitle = "Ready to resolve bit-perfect audio streams.",
                        )
                    )
                )
            )
        )
        return PageDescriptorDto(
            contractVersion = 1,
            json = json.encodeToString(PluginPage.serializer(), page),
        )
    }

    /**
     * Handles user interaction actions dispatched from Server-Driven UI pages.
     */
    override fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto? {
        return ActionResultDto(ActionResultDto.KIND_NONE, "")
    }
}
EOF
elif [ "$PLUGIN_TYPE" = "dsp" ]; then
cat <<EOF > "$KOTLIN_FILE"
package $PLUGIN_ID

import com.bitperfect.player.core.plugin.page.*
import com.bitperfect.player.sdk.plugin.dto.*
import com.bitperfect.player.sdk.plugin.runtime.BitperfectDspPlugin
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk
import kotlinx.serialization.json.Json

/**
 * Bitperfect DSP Plugin: $PLUGIN_NAME
 *
 * Demonstrates:
 * 1. Low-latency real-time DSP filter stage via Linux SharedMemory (Ashmem) zero-copy ring buffer
 * 2. Linux eventfd synchronization between Bitperfect Player audio thread and plugin DSP thread
 * 3. Server-Driven UI: Exposing sliders, toggles, and EQ curves via Pages API v1
 */
class $SERVICE_CLASS : BitperfectDspPlugin() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun capabilities(): Int =
        PluginSdk.CAPABILITY_DSP or PluginSdk.CAPABILITY_PAGES

    /**
     * Negotiates and establishes an out-of-process real-time DSP processing session.
     * Receives SharedMemory and eventfd descriptors from the Bitperfect Player host.
     */
    override fun onOpenDspSession(offer: DspSessionOfferDto): DspSessionDescriptorDto? {
        // Validate offer format (sample rate, channel count, encoding) and return session descriptor
        return null
    }

    /**
     * Renders Server-Driven UI controls for real-time DSP adjustments (e.g. SLIDER, SWITCH).
     */
    override fun onRenderPage(
        routeId: String,
        paramsJson: String?,
        pageToken: String?,
    ): PageDescriptorDto? {
        val page = PluginPage(
            title = "$PLUGIN_NAME Settings",
            subtitle = "Real-time DSP Filter Configuration",
            sections = listOf(
                PluginSection(
                    id = "dsp_controls",
                    kind = "TEXT",
                    header = PluginSectionHeader(title = "DSP Filter Controls"),
                    items = listOf(
                        PluginItem(
                            id = "filter_status",
                            title = "DSP Filter Active",
                            subtitle = "Adjust audio parameters in real-time.",
                        )
                    )
                )
            )
        )
        return PageDescriptorDto(
            contractVersion = 1,
            json = json.encodeToString(PluginPage.serializer(), page),
        )
    }

    /**
     * Handles UI action events dispatched when the user adjusts controls.
     */
    override fun onDispatchAction(actionId: String, paramsJson: String?): ActionResultDto? {
        return ActionResultDto(ActionResultDto.KIND_NONE, "")
    }
}
EOF
else
cat <<EOF > "$KOTLIN_FILE"
package $PLUGIN_ID

import com.bitperfect.player.sdk.plugin.dto.*
import com.bitperfect.player.sdk.plugin.runtime.BitperfectRendererPlugin
import com.bitperfect.player.sdk.plugin.runtime.PluginSdk

/**
 * Bitperfect Output Renderer Plugin: $PLUGIN_NAME
 *
 * Drives external network renderers (e.g. UPnP/DLNA, AirPlay, Cast) by URL.
 */
class $SERVICE_CLASS : BitperfectRendererPlugin() {

    override fun capabilities(): Int =
        PluginSdk.CAPABILITY_OUTPUT_RENDERER

    /**
     * Returns list of discovered renderer endpoints on the network.
     */
    override fun onListRenderers(): List<RendererInfoDto> {
        return emptyList()
    }

    /**
     * Instructs external renderer to play the media stream at hostMediaUrl.
     */
    override fun onPlayOnRenderer(
        rendererId: String,
        track: TrackRefDto,
        hostMediaUrl: String,
    ): Boolean {
        return false
    }

    /**
     * Enqueues the next track on the external renderer for seamless playback.
     */
    override fun onEnqueueNextOnRenderer(
        rendererId: String,
        track: TrackRefDto,
        hostMediaUrl: String,
    ): Boolean {
        return false
    }
}
EOF
fi

echo -e "${GREEN}✓ Successfully scaffolded plugin in '$TARGET_DIR'!${NC}"
echo ""
echo -e "${BOLD}Generated files:${NC}"
echo "  - $TARGET_DIR/build.gradle.kts"
echo "  - $TARGET_DIR/proguard-rules.pro"
echo "  - $TARGET_DIR/src/main/AndroidManifest.xml"
echo "  - $TARGET_DIR/src/main/res/values/strings.xml"
echo "  - $KOTLIN_FILE"
echo ""
echo -e "${BOLD}Next steps:${NC}"
echo "  1. Validate manifest: ./scripts/validate-plugin.sh $TARGET_DIR"
echo "  2. Implement business logic in $SERVICE_CLASS.kt"
echo "  3. Build APK: ./gradlew :$TARGET_DIR:assembleDebug"
