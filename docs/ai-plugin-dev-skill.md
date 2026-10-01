# AI Coding Assistant Skill: Bitperfect Player Plugin Development

This document describes the AI coding assistant skill for **skills.sh**, Claude Code, Cursor, Windsurf, and GitHub Copilot.
The complete, machine-readable skill definition is located at:
📁 [`skills/bitperfect-plugin-dev/SKILL.md`](../skills/bitperfect-plugin-dev/SKILL.md)

---

## Skill Capabilities

When loaded by an AI assistant, this skill provides:
1. **Zero-Trust Sandbox Enforcement**: Ensures plugins never attempt to execute code in the host process or transfer PCM over Binder IPC.
2. **Manifest & Discovery Verification**: Validates `com.bitperfect.plugin.PLUGIN_ID == applicationId`, capability bitmasks, and metadata extension points.
3. **Server-Driven UI (Pages API v1) Generation**: Formats and validates declarative JSON page models adhering to the 120 FPS latency shield, 512 KiB limit, and section specifications.
4. **DSP Audio Engine Architecture**: Scaffolds zero-copy Linux `SharedMemory` ring buffer mapping and `eventfd` signaling meeting the 2.0 ms real-time deadline.
5. **Quickstart Templates**: Provides production-grade boilerplate for streaming sources, DSP equalizers, and settings screens.

---

## Loading the Skill

### skills.sh
```bash
npx skills add bitperfect-player/bitperfect-plugin-dev
```

### Claude / Cursor / Windsurf
Reference `skills/bitperfect-plugin-dev/SKILL.md` in your project prompt or `.cursorrules` / `.windsurfrules`.
