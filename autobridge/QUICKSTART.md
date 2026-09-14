# Quick Start Guide

Get AutoBridge up and running in 5 minutes!

## Prerequisites

- Minecraft Java Edition server (1.21+)
- Geyser-Standalone or Geyser plugin installed
- Java 21 or later

## Installation

### Step 1: Download AutoBridge

Download the latest `AutoBridge-0.1.0-SNAPSHOT.jar` from the [releases page](https://github.com/Leafy-Bot/CombindMC/releases) or build it yourself:

```bash
git clone https://github.com/Leafy-Bot/CombindMC.git
cd CODE/autobridge
build.bat
```

### Step 2: Install the Extension

Copy the JAR file to your Geyser extensions folder:

**Windows:**
```cmd
copy build\AutoBridge-0.1.0-SNAPSHOT.jar C:\path\to\geyser\extensions\
```

**Linux/Mac:**
```bash
cp build/AutoBridge-0.1.0-SNAPSHOT.jar /path/to/geyser/extensions/
```

### Step 3: Start Your Server

Start your Minecraft server as usual. AutoBridge will automatically:
1. Scan your mods directory
2. Extract textures from mod JARs
3. Generate Geyser mappings
4. Build a Bedrock resource pack
5. Register everything with Geyser

You'll see output like:
```
[AutoBridge] AutoBridge initializing — starting full auto-pipeline...
[AutoBridge] Discovered 150 items, 80 blocks from 25 mods (scanned in 245ms)
[AutoBridge] Detected 12 GUI blocks in 15ms
[AutoBridge] Textures: 150 items, 80 blocks processed in 523ms
[AutoBridge] Generated 230 mappings in 89ms
[AutoBridge] Resource pack generated in 156ms
[AutoBridge] AutoBridge auto-pipeline complete (total time: 1028ms)
```

### Step 4: Connect with Bedrock

That's it! Bedrock players can now connect to your server and will automatically:
- See all mod items and blocks
- Receive the auto-generated resource pack
- Interact with mod content seamlessly

## Configuration (Optional)

AutoBridge works out of the box with zero configuration. However, you can customize behavior by editing `autobridge.properties` in your Geyser data directory:

```properties
# Enable/disable caching (recommended: true)
enableCache=true

# Generate placeholder textures when real ones aren't available
generatePlaceholders=true

# Maximum texture size in pixels
maxTextureSize=1024

# Enable detailed logging for debugging
verboseLogging=false

# Automatically detect blocks with GUIs
autoDetectGuiBlocks=true

# Cache expiry time in hours
cacheExpiryHours=24

# Override mods directory (leave empty for auto-detect)
modsDirectoryOverride=
```

## Verifying Installation

### Check Console Output

Look for these messages on server startup:
```
[AutoBridge] AutoBridge initializing — starting full auto-pipeline...
[AutoBridge] AutoBridge auto-pipeline complete
```

### Check Generated Files

In your Geyser data directory, you should see:
- `custom_mappings/items.json` - Item mappings
- `custom_mappings/blocks.json` - Block mappings
- `generated_textures/` - Extracted textures
- `AutoBridge_Pack.zip` - Generated resource pack
- `autobridge.properties` - Configuration file

### Test with Bedrock

1. Connect to your server with a Bedrock client
2. Accept the resource pack when prompted
3. Try using mod items and blocks
4. Everything should work just like Java players!

## Troubleshooting

### "Mods directory does not exist"

Create a `mods` folder in your server directory or set `modsDirectoryOverride` in the config.

### "Failed to register item"

Enable verbose logging:
```properties
verboseLogging=true
```

Check the console for detailed error messages. See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for more help.

### Bedrock players don't see mod content

1. Make sure Bedrock players accept the resource pack
2. Check that mods are in the correct directory
3. Verify Geyser is configured correctly
4. Check console for AutoBridge messages

## Next Steps

- Read the [README.md](README.md) for detailed documentation
- Check [LIMITATIONS.md](LIMITATIONS.md) for known constraints
- See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) if you want to contribute
- Review [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues

## Support

- **GitHub Issues**: https://github.com/Leafy-Bot/CombindMC/issues
- **Geyser Discord**: https://discord.gg/geysermc

## Example Server Setup

Here's a complete example server setup:

```
server/
├── server.jar              # Minecraft server
├── geyser.jar              # Geyser standalone
├── extensions/
│   └── AutoBridge-0.1.0-SNAPSHOT.jar  # AutoBridge
├── mods/                   # Your mod JARs
│   ├── appliedenergistics2.jar
│   ├── create.jar
│   └── ...
└── ...
```

Start the server:
```bash
java -Xmx4G -jar geyser.jar
```

That's it! AutoBridge handles everything automatically.

## Performance Tips

1. **Enable caching** - Makes subsequent starts much faster
2. **Use SSD** - Faster disk I/O for texture extraction
3. **Allocate enough RAM** - At least 4GB for the server
4. **Exclude client-only mods** - Only put server-compatible mods in the mods folder

## What Gets Translated

AutoBridge automatically translates:
- ✅ Items (names, textures, stack sizes)
- ✅ Blocks (names, textures, properties)
- ✅ Basic block states
- ✅ GUI blocks (detected by name patterns)
- ✅ Resource packs (textures, language files)

Limitations:
- ❌ Complex block entities (may need manual configuration)
- ❌ Animated textures (not yet supported)
- ❌ Custom models (uses simple cube geometry)

See [LIMITATIONS.md](LIMITATIONS.md) for details.

---

**Ready to go!** Your modded Java server now supports Bedrock players automatically.

**Last Updated**: 2026-09-14  
**Version**: 0.1.0-SNAPSHOT
