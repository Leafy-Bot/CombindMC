# AutoBridge — Automated Mod Translation Bridge for Geyser

Open-source Geyser Extension that **fully automatically** discovers loaded mods, extracts their textures, generates Bedrock-compatible mappings and resource packs, and registers everything through Geyser's API. **Zero manual configuration per mod. Zero manual mapping.**

## How It Works (Fully Automatic)

```
Server starts → NeoForge loads mods
        ↓
AutoBridge runs full auto-pipeline in one shot:
  1. ModScanner → scans BuiltInRegistries for ALL non-vanilla items/blocks
  2. AutoBlockDetector → identifies blocks with custom GUIs by name/state patterns
  3. TexturePipeline → extracts PNGs from mod JARs, converts to Bedrock format
  4. MappingBuilder → generates Geyser custom_mappings JSON files (with state permutations)
  5. PackBuilder → assembles Bedrock resource pack (.zip) + language file
  6. CacheManager → saves scan results for faster next startup
  7. Geyser Events → auto-registers every item/block via API
        ↓
Bedrock players connect → receive auto-generated pack → see & interact with mod content
```

### What "Automatic" Means

- **No per-mod config files** — scan happens at startup from registries
- **No manual texture mapping** — reads directly from mod JAR assets
- **No manual component assignment** — detects geometry type, stack size, consumable status automatically
- **No manual creative category** — assigns based on detected item behavior
- **No manual language entries** — populates `language.en_us.lang` from registry display names
- **No manual GUI detection** — AutoBlockDetector identifies blocks with interfaces by name patterns and state complexity
- **No manual cache management** — CacheManager persists scan results between restarts, invalidates on mod changes
- **No user interaction required** — install JAR, start server, done

## Architecture

```
┌─────────────────────────────────────────────┐
│         NeoForge 1.26.2 Server              │
│  ┌──────────┐  ┌──────────┐  ┌───────────┐ │
│  │ Your Mods│  │Geyser-   │  │AutoBridge │ │
│  │ (AE2 etc)│  │NeoForge  │  │Extension  │ │
│  └──────────┘  └──────────┘  └───────────┘ │
│                    ▲           │             │
│                    │           ▼             │
│              Bedrock clients ←── Mappings +  │
│              (Xbox/Phone/    Resource Packs  │
│               Windows PC)    (auto-generated)│
└─────────────────────────────────────────────┘
                          ▲
                  Java clients (direct join)
```

## Milestones

1. **M1** ✅ Working Geyser Extension skeleton with full auto-pipeline
2. **M2** Mod scanner reads real NeoForge registries + extracts textures from JARs
3. **M3** Texture pipeline processes from actual JAR assets (not guessed paths)
4. **M4** Full auto-generation pipeline — scan → textures → mappings → pack, all automatic
5. **M5** Block state permutation support + GUI detection + cache persistence
6. **M6** AE2 integration — core blocks and items mapped and visible to Bedrock
7. **M7** Polish, documentation, open-source release

## Build

Requires **Java 25**.

```bash
./gradlew build
```

Place the output JAR in Geyser's `extensions/` folder.

## Dependencies

- Geyser-NeoForge (runtime)
- NeoForge 1.26.2 (server)
- Geyser API `2.11.2-SNAPSHOT`

## License

MIT
