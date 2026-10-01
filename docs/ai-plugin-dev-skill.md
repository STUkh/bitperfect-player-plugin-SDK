# AI Coding Assistant Skill: Bitperfect Player Plugin Development

This document describes the AI coding assistant skill for **skills.sh**, Claude Code, Cursor, Windsurf, and GitHub Copilot.

The complete, machine-readable skill definition and modular references are located at:
- 📁 Playbook: [`skills/bitperfect-plugin-dev/SKILL.md`](../skills/bitperfect-plugin-dev/SKILL.md)
- 📁 SDUI Schema & Vocabulary: [`skills/bitperfect-plugin-dev/references/pages-vocabulary.md`](../skills/bitperfect-plugin-dev/references/pages-vocabulary.md)
- 📁 Out-of-Process AIDL Contracts: [`skills/bitperfect-plugin-dev/references/aidl-contract.md`](../skills/bitperfect-plugin-dev/references/aidl-contract.md)

---

## Trigger Phrases

The skill activates automatically on phrases including:
- `"bitperfect plugin"`
- `"create plugin"`
- `"audio source plugin"`
- `"dsp plugin"`
- `"pages api"`
- `"plugin manifest"`

Scoped tools: `Read`, `Write`, `Edit`, `Glob`, `Grep`, `Bash`.

---

## Skill Capabilities

When loaded by an AI assistant, this skill provides:
1. **Zero-Trust Sandbox Enforcement**: Validates that plugins never execute bytecode in the host process and never transmit PCM over Binder IPC.
2. **Manifest & Discovery Verification**: Enforces the canonical identity invariant (`com.bitperfect.plugin.PLUGIN_ID == applicationId`), capability bitmask matching, and manifest metadata extension points.
3. **Server-Driven UI (Pages API v1) Generation**: Generates and validates declarative JSON page models adhering to the 120 FPS latency shield, 512 KiB limit, and 12 supported section kinds (`LIST`, `GRID`, `SHELF`, `CHIPS`, `HERO_CAROUSEL`, `QUICK_PLAY`, `RADIO`, `BUTTONS`, `SWITCH`, `SLIDER`, `KEY_VALUE`, `TEXT`). See [`references/pages-vocabulary.md`](../skills/bitperfect-plugin-dev/references/pages-vocabulary.md).
4. **Out-of-Process AIDL Contracts**: Provides exact append-only transaction definitions and contracts for `IBitperfectPluginV1`, `IPluginHostCallbackV1`, `IPluginDspSessionV1`, and `IPluginReceiverSessionV1`. See [`references/aidl-contract.md`](../skills/bitperfect-plugin-dev/references/aidl-contract.md).
5. **DSP Audio Engine Architecture**: Scaffolds zero-copy Linux `SharedMemory` ring buffer mapping and `eventfd` signaling meeting the 2.0 ms real-time deadline.
6. **Implementation Templates**: Provides clean boilerplate for streaming sources (`BitperfectSourcePlugin`), DSP filters (`BitperfectDspPlugin`), and settings screens.

---

## Loading the Skill

### skills.sh
```bash
npx skills add bitperfect-player/bitperfect-plugin-dev
```

### Claude / Cursor / Windsurf
Reference `skills/bitperfect-plugin-dev/SKILL.md` in your project prompt or `.cursorrules` / `.windsurfrules`.
