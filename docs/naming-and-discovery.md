# Plugin Naming, Manifest & Discovery Guide

This document defines the strict requirements and best practices for creating, declaring, and naming plugins for **Bitperfect Player**.

---

## 1. Discovery Architecture

Bitperfect Player discovers installed plugins dynamically using Android's `PackageManager.queryIntentServices()`.
The player application does **not** request or require the sensitive `QUERY_ALL_PACKAGES` permission; instead, discovery relies on an explicit manifest contract.

For Bitperfect Player to discover and list your plugin:
1. The plugin must declare an exported Android `Service`.
2. The service must include an `<intent-filter>` with the exact action:
   ```xml
   <action android:name="com.bitperfect.plugin.BIND_PLUGIN" />
   ```
3. The service must declare the required `<meta-data>` tags describing its capabilities and entry points.

---

## 2. Canonical Identity & Package Naming Rules (CRITICAL)

### Rule #1: `PLUGIN_ID` MUST match `applicationId` exactly
The host application uses the OS-derived package name as the canonical identity. When discovering a plugin, Bitperfect Player verifies that the manifest metadata `com.bitperfect.plugin.PLUGIN_ID` **strictly equals** the package name (`applicationId`) resolved by `PackageManager`.

```xml
<!-- Example: If your applicationId is "com.example.bitperfect.plugin.mysource" -->
<meta-data
    android:name="com.bitperfect.plugin.PLUGIN_ID"
    android:value="com.example.bitperfect.plugin.mysource" />
```

> ⚠️ **Security Invariant**: If `PLUGIN_ID` differs from the APK's `applicationId`, the host **immediately rejects** the plugin before admission, approval lookup, or binding. This prevents rogue applications from impersonating other plugins or claiming their stored credentials/permissions.

### Best Practices for Package Naming
To prevent namespace collisions in Google Play or third-party repositories:
* **Recommended format**: `com.<developer-or-org>.bitperfect.plugin.<name>`
  * Example: `com.audiophile.bitperfect.plugin.radio`
  * Example: `org.soundlab.bitperfect.plugin.convolver`
* Keep package names lowercase and stable across all future updates of your plugin APK.

---

## 3. Service Declaration in `AndroidManifest.xml`

Below is a complete, production-ready manifest configuration:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.bitperfect.plugin.mysource">

    <!-- Declare only the permissions your plugin actually needs -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="false"
        android:icon="@drawable/ic_plugin_icon"
        android:label="@string/plugin_name">

        <!-- Exported Service implementing BitperfectSourcePlugin -->
        <service
            android:name=".MySourcePlugin"
            android:exported="true">
            
            <!-- Mandatory Intent Filter for Discovery -->
            <intent-filter>
                <action android:name="com.bitperfect.plugin.BIND_PLUGIN" />
            </intent-filter>

            <!-- 1. Unique Plugin ID (MUST match applicationId) -->
            <meta-data
                android:name="com.bitperfect.plugin.PLUGIN_ID"
                android:value="com.example.bitperfect.plugin.mysource" />

            <!-- 2. Capabilities Bitmask (e.g. SOURCE (1) | PAGES (256) = 257) -->
            <meta-data
                android:name="com.bitperfect.plugin.CAPABILITIES"
                android:value="257" />

            <!-- 3. Minimum Host API Level (Recommended: 6) -->
            <meta-data
                android:name="com.bitperfect.plugin.MIN_HOST_API"
                android:value="6" />

            <!-- 4. Navigation: Mount plugin in player Sidebar -->
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_EXTENSION_ID"
                android:value="my_source_nav" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_TARGET"
                android:value="sidebar" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_LABEL"
                android:value="@string/plugin_sidebar_label" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_ROUTE"
                android:value="home" />
            <meta-data
                android:name="com.bitperfect.plugin.NAVIGATION_ORDER"
                android:value="10" />

            <!-- 5. Settings: Declarative settings route (Pages API v1) -->
            <meta-data
                android:name="com.bitperfect.plugin.SETTINGS_ROUTE"
                android:value="settings" />
        </service>

        <!-- Optional: Activity for interactive Web/OAuth login (if needed) -->
        <activity
            android:name=".auth.LoginActivity"
            android:exported="true"
            android:label="@string/plugin_login_label" />

    </application>
</manifest>
```

---

## 4. Metadata Reference Table

| Key | Type | Description | Required? |
|---|---|---|---|
| `com.bitperfect.plugin.PLUGIN_ID` | String | Unique reverse-DNS identifier matching `applicationId`. | **Yes** |
| `com.bitperfect.plugin.CAPABILITIES` | Integer | Bitmask of declared capabilities (e.g. `257` for SOURCE + PAGES). | **Yes** |
| `com.bitperfect.plugin.MIN_HOST_API` | Integer | Minimum host API level (e.g. `4` for PAGES, `5` for DSP, `6` for extended controls). | **Yes** |
| `com.bitperfect.plugin.NAVIGATION_EXTENSION_ID` | String | Stable ID for sidebar navigation entry. | Required if `NAVIGATION_ROUTE` is set |
| `com.bitperfect.plugin.NAVIGATION_TARGET` | String | Mounting surface in host UI (currently `"sidebar"`). | Optional (defaults to `"sidebar"`) |
| `com.bitperfect.plugin.NAVIGATION_LABEL` | String / Res | Label shown in the host navigation menu. | **Yes** (if navigatable) |
| `com.bitperfect.plugin.NAVIGATION_ROUTE` | String | Route passed to `onRenderPage()` when opening sidebar entry. Matches `[A-Za-z0-9._-]{1,64}`. | Recommended |
| `com.bitperfect.plugin.NAVIGATION_ORDER` | Integer | Ordering weight in sidebar (lower = higher position). | Optional (defaults to 100) |
| `com.bitperfect.plugin.SETTINGS_ROUTE` | String | Route passed to `onRenderPage()` when user opens Settings → Plugins → (Your Plugin). | Optional |
| `com.bitperfect.plugin.SETTINGS_ACTIVITY` | String | Fully qualified class name of Activity for legacy settings UI. | Optional (deprecated by SETTINGS_ROUTE) |

---

## 5. Security & Trust Classification

Bitperfect Player incorporates an out-of-process zero-trust sandbox:
1. **Lazy Binding**: The player only binds to the plugin service when its capability is first invoked.
2. **Explicit User Enablement**: Newly discovered plugins remain in `DISCOVERED` (disabled) state until the user explicitly turns them on in **Settings → Plugins**.
3. **Signer Classification**:
   * **BitperfectSigned**: Official first-party plugins signed with the Bitperfect release certificate.
   * **ThirdParty (Trusted)**: Community plugins whose signing certificate SHA-256 fingerprint is listed in the host allowlist or remote registry (`https://registry.bitperfect.app/plugins/trusted.json`).
   * **Untrusted**: Plugins signed with unverified certificates. When the user enables an untrusted plugin, the player displays a security dialog explaining third-party risks before any IPC connection is made.
4. **Local Development / Debug Mode**:
   * In debug builds of the player (`com.bitperfect.player.debug`), signing checks allow same-signer verification or local testing with your own debug keystore.
