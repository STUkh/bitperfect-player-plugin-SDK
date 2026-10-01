#!/usr/bin/env bash
# ==============================================================================
# validate-plugin.sh - Validate Bitperfect Player Plugin Manifest
# ==============================================================================
# Verifies that an AndroidManifest.xml conforms to the Bitperfect Player Plugin
# Specification (API Level 6):
#   1. Package / applicationId determination
#   2. com.bitperfect.plugin.PLUGIN_ID == applicationId
#   3. Exported Service with action com.bitperfect.plugin.BIND_PLUGIN
#   4. com.bitperfect.plugin.CAPABILITIES metadata exists & non-empty
#   5. com.bitperfect.plugin.MIN_HOST_API metadata exists (>= 4)
# ==============================================================================

set -euo pipefail

# ANSI color codes (disabled if not a tty or NO_COLOR is set)
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
${BOLD}Bitperfect Player Plugin Manifest Validator${NC}

${BOLD}USAGE:${NC}
    $(basename "$0") [OPTIONS] [PATH]

${BOLD}ARGUMENTS:${NC}
    PATH                Path to AndroidManifest.xml OR plugin module directory
                        (default: current directory)

${BOLD}OPTIONS:${NC}
    --id, --package <id>  Explicitly specify expected applicationId/package
    -q, --quiet           Quiet mode (only exit status and errors)
    -v, --verbose         Verbose mode (show detailed capability decoding)
    -h, --help            Show this help message

${BOLD}EXAMPLES:${NC}
    $(basename "$0") sample-plugin
    $(basename "$0") sample-dsp-filter/src/main/AndroidManifest.xml
    $(basename "$0") --package com.example.myplugin path/to/AndroidManifest.xml
EOF
}

TARGET_PATH="."
EXPECTED_PKG=""
QUIET=0
VERBOSE=0

while [ $# -gt 0 ]; do
    case "$1" in
        --id|--package)
            if [ -z "${2:-}" ]; then
                echo -e "${RED}Error: --package requires an argument${NC}" >&2
                exit 1
            fi
            EXPECTED_PKG="$2"
            shift 2
            ;;
        -q|--quiet)
            QUIET=1
            shift
            ;;
        -v|--verbose)
            VERBOSE=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        -*)
            echo -e "${RED}Error: Unknown option '$1'${NC}" >&2
            usage >&2
            exit 1
            ;;
        *)
            TARGET_PATH="$1"
            shift
            ;;
    esac
done

# Resolve manifest path
MANIFEST_FILE=""
MODULE_DIR=""

if [ -f "$TARGET_PATH" ]; then
    MANIFEST_FILE="$TARGET_PATH"
    MODULE_DIR="$(dirname "$MANIFEST_FILE")"
    # If in src/main, module dir is 2 levels up
    if [[ "$MODULE_DIR" == */src/main ]]; then
        MODULE_DIR="$(dirname "$(dirname "$MODULE_DIR")")"
    fi
elif [ -d "$TARGET_PATH" ]; then
    MODULE_DIR="$TARGET_PATH"
    if [ -f "$MODULE_DIR/src/main/AndroidManifest.xml" ]; then
        MANIFEST_FILE="$MODULE_DIR/src/main/AndroidManifest.xml"
    elif [ -f "$MODULE_DIR/AndroidManifest.xml" ]; then
        MANIFEST_FILE="$MODULE_DIR/AndroidManifest.xml"
    else
        echo -e "${RED}[FAIL] Could not find AndroidManifest.xml in '$TARGET_PATH'${NC}" >&2
        exit 1
    fi
else
    echo -e "${RED}[FAIL] Target path '$TARGET_PATH' does not exist.${NC}" >&2
    exit 1
fi

# Detect application ID / package name if not provided
if [ -z "$EXPECTED_PKG" ]; then
    # 1. Try reading package attribute from <manifest> in XML
    PKG_FROM_XML=$(grep -E '<manifest[^>]+package="[^"]+"' "$MANIFEST_FILE" | sed -E 's/.*package="([^"]+)".*/\1/' || true)
    if [ -n "$PKG_FROM_XML" ]; then
        EXPECTED_PKG="$PKG_FROM_XML"
    fi
fi

if [ -z "$EXPECTED_PKG" ] && [ -n "$MODULE_DIR" ]; then
    # 2. Try reading from build.gradle.kts or build.gradle in module dir
    GRADLE_FILE=""
    if [ -f "$MODULE_DIR/build.gradle.kts" ]; then
        GRADLE_FILE="$MODULE_DIR/build.gradle.kts"
    elif [ -f "$MODULE_DIR/build.gradle" ]; then
        GRADLE_FILE="$MODULE_DIR/build.gradle"
    fi

    if [ -n "$GRADLE_FILE" ]; then
        PKG_FROM_GRADLE=$(sed -nE 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$GRADLE_FILE" | head -n 1 || true)
        if [ -z "$PKG_FROM_GRADLE" ]; then
            PKG_FROM_GRADLE=$(sed -nE 's/^[[:space:]]*namespace[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$GRADLE_FILE" | head -n 1 || true)
        fi
        if [ -n "$PKG_FROM_GRADLE" ]; then
            EXPECTED_PKG="$PKG_FROM_GRADLE"
        fi
    fi
fi

if [ $QUIET -eq 0 ]; then
    echo -e "${BOLD}Validating Bitperfect Plugin Manifest:${NC} $MANIFEST_FILE"
    if [ -n "$EXPECTED_PKG" ]; then
        echo -e "  Expected Application ID: ${BLUE}$EXPECTED_PKG${NC}"
    fi
    echo ""
fi

# Validation state
ERRORS=()
WARNINGS=()
PASSES=()

# Run python validator if python3 is available (recommended for XML parsing),
# with pure-shell fallbacks.
validate_with_python() {
    python3 - "$MANIFEST_FILE" "$EXPECTED_PKG" << 'PYEOF'
import sys
import xml.etree.ElementTree as ET
import re

manifest_path = sys.argv[1]
expected_pkg = sys.argv[2] if len(sys.argv) > 2 else ""

errors = []
warnings = []
passes = []

try:
    tree = ET.parse(manifest_path)
    root = tree.getroot()
except Exception as e:
    print(f"FATAL:Failed to parse XML: {e}")
    sys.exit(1)

# Check package
manifest_pkg = root.attrib.get("package", "")
effective_pkg = expected_pkg or manifest_pkg

if not effective_pkg:
    errors.append("Package/applicationId could not be determined. Declare package in AndroidManifest.xml or build.gradle.kts, or pass --package <id>.")
else:
    passes.append(f"Application / Package ID: {effective_pkg}")

# Find application and services
app = root.find("application")
if app is None:
    errors.append("<application> element missing in AndroidManifest.xml.")
    services = []
else:
    services = app.findall("service")

if not services:
    errors.append("No <service> elements declared inside <application>.")

# Search for the Bitperfect plugin service
plugin_service = None
bind_action_found = False
exported_properly = False

for svc in services:
    for intent_filter in svc.findall("intent-filter"):
        for action in intent_filter.findall("action"):
            name = action.attrib.get("{http://schemas.android.com/apk/res/android}name") or action.attrib.get("name")
            if name == "com.bitperfect.plugin.BIND_PLUGIN":
                bind_action_found = True
                plugin_service = svc
                exported_val = svc.attrib.get("{http://schemas.android.com/apk/res/android}exported") or svc.attrib.get("android:exported") or svc.attrib.get("exported")
                if exported_val == "true":
                    exported_properly = True
                break

if not bind_action_found:
    errors.append("Missing plugin Service with <action android:name=\"com.bitperfect.plugin.BIND_PLUGIN\" />.")
elif not exported_properly:
    svc_name = plugin_service.attrib.get("{http://schemas.android.com/apk/res/android}name") or plugin_service.attrib.get("name", "Service")
    errors.append(f"Plugin Service '{svc_name}' declares BIND_PLUGIN but is NOT exported. Must specify android:exported=\"true\".")
else:
    svc_name = plugin_service.attrib.get("{http://schemas.android.com/apk/res/android}name") or plugin_service.attrib.get("name", "Service")
    passes.append(f"Exported Service with BIND_PLUGIN: {svc_name}")

# If we found a plugin service, check its meta-data
meta_items = {}
if plugin_service is not None:
    for meta in plugin_service.findall("meta-data"):
        m_name = meta.attrib.get("{http://schemas.android.com/apk/res/android}name") or meta.attrib.get("name")
        m_val = meta.attrib.get("{http://schemas.android.com/apk/res/android}value") or meta.attrib.get("value")
        if m_name:
            meta_items[m_name] = m_val or ""

# Check PLUGIN_ID
plugin_id = meta_items.get("com.bitperfect.plugin.PLUGIN_ID")
if plugin_id is None:
    errors.append("Missing required <meta-data android:name=\"com.bitperfect.plugin.PLUGIN_ID\" ... /> in plugin Service.")
elif not plugin_id.strip():
    errors.append("com.bitperfect.plugin.PLUGIN_ID value cannot be empty.")
else:
    if effective_pkg and plugin_id != effective_pkg:
        errors.append(f"PLUGIN_ID mismatch: expected '{effective_pkg}' (must strictly match applicationId), but found '{plugin_id}'.")
    else:
        passes.append(f"PLUGIN_ID matches applicationId: {plugin_id}")

# Check CAPABILITIES
capabilities_raw = meta_items.get("com.bitperfect.plugin.CAPABILITIES")
if capabilities_raw is None:
    errors.append("Missing required <meta-data android:name=\"com.bitperfect.plugin.CAPABILITIES\" ... /> in plugin Service.")
elif not capabilities_raw.strip():
    errors.append("com.bitperfect.plugin.CAPABILITIES value cannot be empty.")
else:
    try:
        cap_val = int(capabilities_raw.strip())
        if cap_val <= 0:
            errors.append(f"CAPABILITIES bitmask must be a positive integer > 0 (found: {cap_val}).")
        else:
            cap_names = []
            if cap_val & 1: cap_names.append("SOURCE (1)")
            if cap_val & (1 << 1): cap_names.append("METADATA (2)")
            if cap_val & (1 << 2): cap_names.append("OUTPUT_RENDERER (4)")
            if cap_val & (1 << 3): cap_names.append("UI_TABS (8)")
            if cap_val & (1 << 4): cap_names.append("NOW_PLAYING_EXT (16)")
            if cap_val & (1 << 5): cap_names.append("SETTINGS (32)")
            if cap_val & (1 << 6): cap_names.append("AUTH (64)")
            if cap_val & (1 << 7): cap_names.append("CLOUD_SOURCE (128)")
            if cap_val & (1 << 8): cap_names.append("PAGES (256)")
            if cap_val & (1 << 9): cap_names.append("DSP (512)")
            if cap_val & (1 << 10): cap_names.append("RECEIVER (1024)")
            desc = " | ".join(cap_names) if cap_names else "CUSTOM"
            passes.append(f"CAPABILITIES: {cap_val} [{desc}]")
    except ValueError:
        errors.append(f"CAPABILITIES must be an integer bitmask, found: '{capabilities_raw}'.")

# Check MIN_HOST_API
min_api_raw = meta_items.get("com.bitperfect.plugin.MIN_HOST_API")
if min_api_raw is None:
    errors.append("Missing required <meta-data android:name=\"com.bitperfect.plugin.MIN_HOST_API\" ... /> in plugin Service.")
else:
    try:
        min_api_val = int(min_api_raw.strip())
        if min_api_val < 4:
            errors.append(f"MIN_HOST_API level '{min_api_val}' is too low (must be >= 4, recommended 6).")
        else:
            passes.append(f"MIN_HOST_API: {min_api_val} (>= 4)")
    except ValueError:
        errors.append(f"MIN_HOST_API must be an integer >= 4, found: '{min_api_raw}'.")

# Optional navigation check
nav_id = meta_items.get("com.bitperfect.plugin.NAVIGATION_EXTENSION_ID")
nav_target = meta_items.get("com.bitperfect.plugin.NAVIGATION_TARGET")
if nav_id and nav_target:
    passes.append(f"Navigation integration: target='{nav_target}', id='{nav_id}'")

# Emit results
for p in passes:
    print(f"PASS:{p}")
for w in warnings:
    print(f"WARN:{w}")
for e in errors:
    print(f"FAIL:{e}")

if errors:
    sys.exit(1)
else:
    sys.exit(0)
PYEOF
}

# Run validation
set +e
OUTPUT=$(validate_with_python 2>&1)
EXIT_CODE=$?
set -e

# Parse output
while IFS= read -r line; do
    if [[ "$line" =~ ^PASS:(.*)$ ]]; then
        PASSES+=("${BASH_REMATCH[1]}")
    elif [[ "$line" =~ ^WARN:(.*)$ ]]; then
        WARNINGS+=("${BASH_REMATCH[1]}")
    elif [[ "$line" =~ ^FAIL:(.*)$ ]]; then
        ERRORS+=("${BASH_REMATCH[1]}")
    elif [[ "$line" =~ ^FATAL:(.*)$ ]]; then
        ERRORS+=("${BASH_REMATCH[1]}")
    fi
done <<< "$OUTPUT"

# Display passes
if [ $QUIET -eq 0 ]; then
    if [ ${#PASSES[@]} -gt 0 ]; then
        for p in "${PASSES[@]}"; do
            echo -e "  ${GREEN}✓${NC} $p"
        done
    fi
    if [ ${#WARNINGS[@]} -gt 0 ]; then
        for w in "${WARNINGS[@]}"; do
            echo -e "  ${YELLOW}!${NC} $w"
        done
    fi
fi

# Display errors
if [ ${#ERRORS[@]} -gt 0 ]; then
    echo ""
    echo -e "${RED}${BOLD}Validation failed with ${#ERRORS[@]} error(s):${NC}"
    for e in "${ERRORS[@]}"; do
        echo -e "  ${RED}✗${NC} $e"
    done
    echo ""
    echo -e "${BOLD}Action required:${NC}"
    echo "  Update '$MANIFEST_FILE' to satisfy all Bitperfect Player Plugin requirements."
    echo "  Refer to docs/plugin-architecture.md for complete specification."
    exit 1
else
    if [ $QUIET -eq 0 ]; then
        echo ""
        echo -e "${GREEN}${BOLD}[PASS] Plugin manifest is valid and production-ready!${NC}"
    fi
    exit 0
fi
