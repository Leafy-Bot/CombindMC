# AutoBridge

**Fully automatic mod translation bridge for Geyser — Java mods → Bedrock crossplay with zero manual config.**

## Status

✅ **Compiles clean** against Geyser API 2.11.2-SNAPSHOT (Java 25)
✅ **7/7 tests pass** — full pipeline validated without Minecraft server
✅ **JAR-based scanner** — reads mod JARs directly, no Minecraft runtime needed

## What It Does

When you drop AutoBridge into Geyser's `extensions/` folder and put mod JARs in the `mods/` directory:

1. **Scans** all mod JARs for items, blocks, and textures
2. **Extracts** textures from JARs (or generates placeholders)
3. **Generates** Geyser custom_mappings JSON automatically
4. **Builds** a Bedrock resource pack with all mod content
5. **Registers** everything via Geyser's API — Bedrock players see mod content automatically

No per-mod config. No manual mapping. No user intervention.

## Architecture

```
Server starts → Geyser loads AutoBridge extension
        ↓
AutoBridge auto-pipeline:
  1. ModScanner → reads JARs, discovers items/blocks/textures
  2. AutoBlockDetector → identifies GUI blocks (AE2 patterns)
  3. TexturePipeline → extracts PNGs, generates placeholders
  4. MappingBuilder → generates custom_mappings JSON
  5. PackBuilder → assembles Bedrock resource pack
  6. CacheManager → saves results for faster restarts
  7. Geyser Events → registers items/blocks/pack automatically
        ↓
Bedrock players connect → receive pack → see & interact with mod content
```

## Pipeline Modules

| Module | File | Purpose |
|---|---|---|
| **ModScanner** | `ModScanner.java` | Reads mod JARs directly — parses neoforge.mods.toml, discovers items/blocks/textures |
| **TexturePipeline** | `TexturePipeline.java` | Extracts textures from JARs, generates colored placeholders |
| **MappingBuilder** | `MappingBuilder.java` | Generates Geyser custom_mappings JSON (items + blocks with state permutations) |
| **PackBuilder** | `PackBuilder.java` | Assembles Bedrock resource pack (manifest + textures + language file) |
| **AutoBlockDetector** | `AutoBlockDetector.java` | Detects GUI blocks by name patterns (AE2: me_controller, me_terminal, etc.) |
| **CacheManager** | `CacheManager.java` | Persists scan results, invalidates on mod changes |
| **GuiTranslator** | `GuiTranslator.java` | Generates form descriptions for mod GUIs (ME Terminal, Crafting CPU) |
| **AutoBridge** | `AutoBridge.java` | Geyser Extension — wires pipeline to Geyser events |

## Testing

```bash
# Compile pipeline modules (no Geyser needed)
javac -d build/classes src/main/java/autobridge/ModScanner.java \
  src/main/java/autobridge/TexturePipeline.java \
  src/main/java/autobridge/MappingBuilder.java \
  src/main/java/autobridge/PackBuilder.java \
  src/main/java/autobridge/AutoBlockDetector.java \
  src/main/java/autobridge/CacheManager.java \
  src/main/java/autobridge/GuiTranslator.java

# Compile test harness
javac -cp build/classes -d build/classes \
  src/test/java/autobridge/TestHarness.java

# Run tests
java -cp build/classes autobridge.TestHarness
```

**Test results:**
```
[TEST 1] ModScanner... PASSED
[TEST 2] TexturePipeline... PASSED (placeholder 107 bytes)
[TEST 3] MappingBuilder... PASSED (items.json 977 bytes, blocks.json 1675 bytes)
[TEST 4] PackBuilder... PASSED (AutoBridge_Pack.zip generated)
[TEST 5] CacheManager... PASSED (save/load/invalidation)
[TEST 6] AutoBlockDetector... PASSED (2 GUI blocks detected, AE2 patterns matched)
[TEST 7] GuiTranslator... PASSED (Form[ME Terminal, elements=4])

=== Results: 7/7 tests passed ===
```

## Compiling AutoBridge (Geyser Extension)

```bash
# Download Geyser API jars to libs/
# Then compile with classpath:
javac -cp "build/classes;libs/geyser-api.jar;libs/base-api.jar;libs/events.jar;libs/annotations.jar" \
  -d build/classes src/main/java/autobridge/AutoBridge.java
```

## Geyser API Usage (Verified)

All API calls verified against Geyser API 2.11.2-SNAPSHOT via `javap`:

- `@Subscribe` from `org.geysermc.event.subscribe`
- `ExtensionLogger` (not slf4j) — `info()`, `error()`, `warning()`, `debug()`
- `NonVanillaCustomItemDefinition.builder(javaId, bedrockId, networkId)` — 3-arg builder
- `NonVanillaCustomBlockData.builder()` — for modded blocks
- `GeyserDefineResourcePacksEvent.register(ResourcePack)` — non-deprecated pack registration
- `GeometryComponent.builder().identifier("minecraft:geometry.full_block")` — takes GeometryComponent, not String
- `MaterialInstance.builder().texture(name).renderMethod("alphatest")`
- `JavaItemDataComponents.MAX_STACK_SIZE`, `CONSUMABLE`, `SWING_ANIMATION`
- `GeyserItemDataComponents.ATTACK_DAMAGE`, `BLOCK_PLACER`
- `JavaConsumable.builder().consumeSeconds(1.0f).animation(Animation.EAT)`
- `JavaSwingAnimation.builder().duration(12)` — int ticks, not float seconds
- `GeyserBlockPlacer.builder().block(Identifier).useBlockIcon(false)`

## Server Setup

1. Install Geyser-NeoForge on your server
2. Place `AutoBridge.jar` in Geyser's `extensions/` folder
3. Put mod JARs in the `mods/` directory
4. Start server — AutoBridge handles everything automatically
5. Bedrock players connect and receive the auto-generated resource pack

See [SETUP.md](SETUP.md) for detailed instructions.

## Limitations

See [LIMITATIONS.md](LIMITATIONS.md) for known constraints.

## License

MIT
