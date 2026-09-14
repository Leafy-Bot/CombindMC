# Current Limitations

Updated: project compiles clean, 7/7 tests pass.

## Hard Limitations (From Geyser/Bedrock)

These cannot be fixed by AutoBridge — they require changes to Bedrock or the Java protocol itself.

### GUI Limitations
- **Complex custom GUIs** — AE2's ME Terminal, Crafting CPU interface, and similar dynamic GUIs cannot be rendered natively on Bedrock. AutoBridge provides simplified form alternatives, but drag-and-drop, shift-click, and complex layouts are not possible.
- **Item highlighting in custom GUIs** — Known Geyser bug (#5896). Items may not highlight correctly when hovered over in translated forms.
- **Custom enchantment display** — Non-vanilla enchantment levels (e.g., "Silk Touch 2") cannot be shown to Bedrock players.

### Visual Limitations
- **Item displays** — Minecraft's `Display` system (item frames with floating items) is not supported by Geyser.
- **Glowing effect** — Not visible to Bedrock players.
- **Dolphin's Grace visuals** — Effect works but visual bubbles are missing.

### Interaction Limitations
- **Left vs right click in inventories** — Bedrock sends no packet distinguishing these.
- **Chat clickable links** — Bedrock clients don't support link packets that Java does.

## Soft Limitations (AutoBridge-Specific)

### Texture Translation
- **Procedural/dynamic textures** — Mods that generate textures at runtime (not from files) cannot be captured.
- **Animated textures** — Flipbook animations work for single-texture items but not multi-frame block animations.
- **Placeholder textures** — Items/blocks without extractable textures get colored placeholders (namespace-hashed color + first letter).

### Mapping Coverage
- **Block state permutations** — Blocks with many states are capped at 16 permutations (Bedrock limit).
- **Complex block geometry** — All blocks currently use `minecraft:geometry.full_block`. Complex shapes (stairs, slabs, fences) need per-model geometry extraction.
- **Item model parsing** — Currently discovers items by model file presence, not by parsing model JSON for texture references.

### Scanner
- **TOML parsing** — Simplified parser handles common `modId = "value"` patterns but not full TOML spec.
- **Fabric support** — Basic fabric.mod.json parsing implemented but not extensively tested.
- **No registry access** — Scanner reads JARs directly; it doesn't see runtime-registered content (e.g., items added by other mods at runtime).

### Performance
- **Startup scanning** — Scanning all mod JARs adds time proportional to JAR count. For 50+ mods, expect 2-5 seconds.
- **Resource pack size** — Generated pack grows with each mod. A 50-mod pack may be 50-100MB.

## What Works

- ✅ Scans NeoForge/Fabric mod JARs for items, blocks, textures
- ✅ Extracts textures from JARs or generates placeholders
- ✅ Generates valid Geyser custom_mappings JSON
- ✅ Builds valid Bedrock resource pack (.zip with manifest)
- ✅ Detects AE2 GUI blocks by name pattern
- ✅ Cache persistence between restarts
- ✅ Compiles against real Geyser API 2.11.2-SNAPSHOT
- ✅ All 7 test harness tests pass

## Reporting New Limitations

If you encounter a limitation not listed here:
1. Check the server logs for specific error messages
2. Note the mod name, version, and the specific block/item that doesn't work
3. Document what you expected vs. what Bedrock players see
4. Submit details to the AutoBridge issue tracker
