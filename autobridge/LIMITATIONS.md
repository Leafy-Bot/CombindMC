# Current Limitations

This document tracks known limitations of AutoBridge and the underlying Geyser ecosystem. Updated as we progress through milestones.

## Hard Limitations (From Geyser/Bedrock)

These cannot be fixed by AutoBridge — they require changes to Bedrock or the Java protocol itself.

### GUI Limitations
- **Complex custom GUIs** — AE2's ME Terminal, Crafting CPU interface, and similar dynamic GUIs cannot be rendered natively on Bedrock. AutoBridge provides simplified Cumulus form alternatives, but drag-and-drop, shift-click, and complex layouts are not possible.
- **Item highlighting in custom GUIs** — Known Geyser bug (#5896). Items may not highlight correctly when hovered over in translated forms.
- **Custom enchantment display** — Non-vanilla enchantment levels (e.g., "Silk Touch 2") cannot be shown to Bedrock players.

### Visual Limitations
- **Item displays** — Minecraft's `Display` system (item frames with floating items) is not supported by Geyser. Mods using this will show nothing to Bedrock players unless third-party extensions like `GeyserDisplayEntity` are used.
- **Custom elytra** — Work visually only due to Bedrock limitations.
- **Glowing effect** — Not visible to Bedrock players.
- **Dolphin's Grace visuals** — Effect works but visual bubbles are missing.

### Interaction Limitations
- **Left vs right click in inventories** — Bedrock sends no packet distinguishing these. AutoBridge cannot differentiate between placing and breaking items in custom GUIs.
- **Chat clickable links** — Bedrock clients don't support link packets that Java does.
- **Redstone dot blockstates** — Some client-side controlled block states don't translate.

### Data Limitations
- **Potion colors via PotionContents** — Custom potion colors defined through data components don't translate.
- **Custom anvil/smithing recipes** — Custom ingredients and patterns aren't recognized.
- **Custom beacon base blocks** — Hardcoded in Bedrock edition.

## Soft Limitations (AutoBridge-Specific)

These are areas where AutoBridge can improve but currently has constraints.

### Texture Translation
- **Procedural/dynamic textures** — Mods that generate textures at runtime (not from files) cannot be captured by the texture pipeline.
- **Animated textures** — Flipbook animations work for single-texture items but not for multi-frame block animations.
- **Texture atlas conflicts** — If multiple mods use overlapping texture paths, the last-loaded mod wins.

### Mapping Coverage
- **Non-vanilla items require API registration** — Items registered through mod-specific APIs (not standard registries) may not be detected by the scanner.
- **Predicate-based items** — Items that change behavior based on predicates (broken state, damage level, etc.) are simplified. All predicate variants map to a single Bedrock definition.
- **Block permutations** — Blocks with many state variants (e.g., 16 wool colors) are mapped as a single Bedrock block. State-dependent visual differences may be lost.

### Network ID Allocation
- **Sequential allocation** — IDs are assigned sequentially starting at 1000. If AutoBridge is updated and re-registers items, IDs may shift, potentially causing stale references.
- **Cross-mod collisions** — Two mods defining the same Java item ID would collide. This is prevented by namespace isolation but could occur if mods share namespaces incorrectly.

### Performance
- **Startup scanning overhead** — Scanning all mod registries adds ~2-5 seconds to server startup time for large modpacks (200+ mods).
- **Resource pack size** — The generated pack grows with each added mod. A 50-mod pack may be 50-100MB, which increases download time for new Bedrock players.

## Milestone-Specific Limitations

### M1-M3 (Current Phase)
- **Simulation mode** — When running outside a Minecraft server context, the ModScanner falls back to simulated/test data. Real registry scanning requires NeoForge runtime.
- **Placeholder textures** — Items/blocks without extractable textures get colored placeholders instead of actual mod textures.
- **No GUI translation yet** — The GuiTranslator module is scaffolded but not integrated with actual GUI events.

### M4 (Current Phase)
- **JAR asset extraction** — TexturePipeline reads from mod JARs via `JarFile` API. Works for standard asset layouts; non-standard or dynamically-generated textures may not be found.
- **Placeholder generation** — Items/blocks without extractable textures get deterministic colored placeholders (namespace-hashed color + first letter). Not visually identical to real textures but identifies the mod.
- **No GUI translation yet** — The GuiTranslator module is scaffolded with AE2-specific handlers but not integrated with actual GUI open events.
- **Simulation mode fallback** — When registries aren't available (dev/testing outside server), falls back to AE-style test data.

## Reporting New Limitations

If you encounter a limitation not listed here:
1. Check the server logs for specific error messages
2. Note the mod name, version, and the specific block/item that doesn't work
3. Document what you expected vs. what Bedrock players see
4. Submit details to the AutoBridge issue tracker
