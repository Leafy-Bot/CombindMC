# AutoBridge — Server Setup Guide

This guide walks you through setting up a NeoForge server with Geyser, Floodgate, and AutoBridge for full Java + Bedrock crossplay with modded content.

## Prerequisites

- **Java 21+** (Java 25 recommended)
- A machine with at least 8GB RAM dedicated to the server
- Port access for TCP 25565 (Java) and UDP 19132 (Bedrock)
- Minecraft Java Edition 1.21+ clients (for Java players)
- Minecraft Bedrock Edition clients (for Bedrock players)

## Step 1: Install NeoForge

Download NeoForge for Minecraft 1.26.2 from [neoforged.net](https://neoforged.net/).

```bash
# Example: installing NeoForge installer
java -jar NeoForge-installer-X.X.X-installer.jar --server
```

This creates a `server` directory with the base NeoForge installation.

## Step 2: Install Geyser-NeoForge

1. Download Geyser-NeoForge from [geysermc.org/download](https://geysermc.org/download?project=geyser&expanded=geyser)
2. Place the JAR in the server's `mods/` folder
3. Start the server once to generate default configuration
4. Stop the server

The server generates:
```
config/Geyser-NeoForge/config.yml
config/Geyser-Neo-Floodgate/key.pem
```

## Step 3: Configure Geyser

Edit `config/Geyser-NeoForge/config.yml`:

```yaml
bedrock:
  # Port Bedrock players connect to (UDP)
  port: 19132
  
  # Allow both Java and Bedrock on same port
  # clone-remote-port: false  # Keep this disabled for separate ports
  
  # Address to bind (0.0.0.0 = all interfaces)
  address: 0.0.0.0

auth-type: floodgate  # IMPORTANT: enables Bedrock-only login

online-mode: false  # Set to true if you want online mode for Java players
# But keep auth-type: floodgate so Bedrock players can join without Java accounts

log-levels:
  geyser: INFO
  floodgate: INFO
```

## Step 4: Enable Custom Content

In the same `config.yml`, ensure:

```yaml
gameplay:
  enable-custom-content: true  # REQUIRED for AutoBridge to work
```

## Step 5: Install Floodgate

Floodgate is required for Bedrock players to authenticate without Java accounts.

1. Download Floodgate-NeoForge from [geysermc.org/download](https://geysermc.org/download?project=floodgate&expanded=floodgate)
2. Place the JAR in the server's `mods/` folder alongside Geyser

Floodgate generates a key pair (`key.pem`) used to identify Bedrock players. **Do not share this file.**

## Step 6: Install AutoBridge

1. Build AutoBridge:
```bash
cd autobridge
./gradlew build
```

2. The built JAR appears in `autobridge/build/libs/AutoBridge-0.1.0-SNAPSHOT.jar`

3. Copy it to Geyser's extensions folder:
```bash
cp build/libs/AutoBridge-0.1.0-SNAPSHOT.jar ../server/extensions/
```

If the `extensions/` directory doesn't exist inside the server folder, create it:
```bash
mkdir -p server/extensions
```

## Step 7: Add Your Mods

Place your mod JARs in the server's `mods/` folder:

```
server/mods/
├── appliedenergistics-2-X.X.X.jar      # AE2
├── other-mod-X.X.X.jar                  # More mods
└── ...
```

**Important:** Only include mods that are **server-side compatible**. Client-only mods will not work for Bedrock players even with AutoBridge.

## Step 8: Start the Server

```bash
java -Xmx4G -Xms2G -jar NeoForge-server.jar nogui
```

Watch the logs for AutoBridge initialization:

```
[AutoBridge] AutoBridge 0.1.0-SNAPSHOT initializing...
[AutoBridge] AutoBridge pipeline modules initialized
[AutoBridge] Scanning mods for custom items...
[AutoBridge/Scanner] Scanned X items, found Y from mods
[AutoBridge/Mappings] Generated item mappings...
[AutoBridge/Pack] Generated resource pack → AutoBridge_Pack.zip
[AutoBridge] Registered Z custom items total
[AutoBridge] Registered W custom blocks total
```

## Step 9: Connect Players

### Java Players
Add server normally using IP:PORT (default 25565).

### Bedrock Players
On Xbox/Phone/Windows PC:
1. Open Minecraft → Play → Servers
2. Add new server
3. Address: `<your-server-ip>`
4. Port: `19132` (the Bedrock port from config.yml)
5. Save and connect

Bedrock players will automatically receive the AutoBridge resource pack containing textures and mappings for all modded content.

## Troubleshooting

### "No resource pack generated" in logs
- Ensure `enable-custom-content: true` in config.yml
- Check that mods are loading before Geyser initializes
- Verify AutoBridge JAR is in the correct `extensions/` folder

### Bedrock players see purple/black missing textures
- The texture pipeline couldn't extract textures from the mod
- Check logs for "No texture found for..." warnings
- This is expected for mods with complex/custom texture systems
- Contribute to AutoBridge by submitting texture extraction fixes

### "Failed to register custom item/block" errors
- Network ID collision — AutoBridge handles this automatically
- Registry access issue — may indicate mod incompatibility
- Check the specific error message for details

### Bedrock players can't break certain blocks
- Some block states aren't fully translated yet
- Check the current limitations list in README.md
- Report issues with the specific block name

## Advanced Configuration

### Resource Pack Settings

In `config.yml`:
```yaml
geyser:
  packs:
    enabled: true
    fail-on-fail: false
    forced: true  # Force Bedrock players to accept the pack
```

### Debug Logging

For troubleshooting, increase log verbosity:
```yaml
log-levels:
  geyser: DEBUG
  floodgate: DEBUG
  autobridge: DEBUG
```

### Multiple Mod Packs

If you run multiple servers with different modpacks, each server gets its own AutoBridge extension instance that auto-generates pack-specific content.

## Maintenance

### Updating AutoBridge

1. Stop the server
2. Replace the JAR in `extensions/` with the new version
3. Start the server — AutoBridge regenerates all mappings automatically

### Clearing Generated Files

To force a full regeneration:
```bash
rm -rf server/extensions/custom_mappings/*
rm -rf server/extensions/generated_*
rm -rf server/extensions/packs/*
```

Then restart the server.
