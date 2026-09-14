# Troubleshooting Guide

This guide helps you diagnose and fix common issues with AutoBridge.

## Common Issues

### Issue: "Mods directory does not exist"

**Symptoms:**
```
[ModScanner] Mods directory does not exist: C:\path\to\mods
```

**Causes:**
- Mods directory not created
- Incorrect path in configuration
- Permissions issue

**Solutions:**

1. **Create the mods directory**
   ```bash
   mkdir mods
   ```

2. **Set mods directory override in config**
   Edit `autobridge.properties`:
   ```properties
   modsDirectoryOverride=C:/path/to/your/mods
   ```

3. **Check permissions**
   - Ensure the server has read access to the mods directory
   - On Windows, right-click → Properties → Security
   - On Linux/Mac, use `chmod` to set permissions

---

### Issue: "Failed to register item"

**Symptoms:**
```
[AutoBridge] Failed to register item modid:item_name: <error message>
```

**Causes:**
- Geyser API version mismatch
- Invalid item definition
- Duplicate item registration

**Solutions:**

1. **Check Geyser version**
   - AutoBridge requires Geyser API 2.11.2-SNAPSHOT
   - Update Geyser to the latest version
   - Verify API compatibility

2. **Enable verbose logging**
   Edit `autobridge.properties`:
   ```properties
   verboseLogging=true
   ```
   Check console for detailed error messages.

3. **Check for duplicates**
   - Ensure no other extension registers the same items
   - Check for conflicting mods

---

### Issue: "Texture extraction failed"

**Symptoms:**
```
[TexturePipeline] Failed to extract texture for item modid:item_name
```

**Causes:**
- Texture not found in mod JAR
- Invalid texture format
- JAR file corrupted

**Solutions:**

1. **Verify mod JAR structure**
   Expected structure:
   ```
   mod.jar
   └── assets/
       └── modid/
           ├── models/
           │   └── item/
           │       └── item_name.json
           └── textures/
               └── items/
                   └── item_name.png
   ```

2. **Check texture format**
   - Must be PNG format
   - Recommended size: 16x16 or 32x32
   - Must be valid PNG (not corrupted)

3. **Enable placeholder generation**
   Edit `autobridge.properties`:
   ```properties
   generatePlaceholders=true
   ```
   This creates colored placeholders when textures can't be extracted.

---

### Issue: "Cache load failed"

**Symptoms:**
```
[CacheManager] Failed to load cache: <error message>
```

**Causes:**
- Cache file corrupted
- Insufficient disk space
- Permissions issue

**Solutions:**

1. **Clear the cache**
   Delete the cache directory:
   ```bash
   rm -rf <geyser-data>/cache
   ```
   Or on Windows:
   ```cmd
   rmdir /s /q <geyser-data>\cache
   ```

2. **Disable caching temporarily**
   Edit `autobridge.properties`:
   ```properties
   enableCache=false
   ```

3. **Check disk space**
   - Ensure sufficient disk space for cache
   - Cache size is typically 1-10 MB per 100 mods

---

### Issue: "Resource pack generation failed"

**Symptoms:**
```
[PackBuilder] Failed to generate resource pack: <error message>
```

**Causes:**
- Insufficient disk space
- Permissions issue
- Invalid texture data

**Solutions:**

1. **Check disk space**
   - Resource packs are typically 1-50 MB
   - Ensure sufficient space in Geyser data directory

2. **Check permissions**
   - Ensure write access to Geyser data directory
   - Check antivirus software isn't blocking file creation

3. **Regenerate textures**
   Delete generated textures:
   ```bash
   rm -rf <geyser-data>/generated_textures
   ```
   Restart server to regenerate.

---

### Issue: "GUI block not detected"

**Symptoms:**
- Block with GUI not recognized
- Bedrock players can't interact with GUI

**Causes:**
- Block name doesn't match known patterns
- Custom GUI not in pattern database

**Solutions:**

1. **Check block name**
   Auto-detection looks for patterns like:
   - `terminal`, `controller`, `cpu`
   - `machine`, `press`, `assembler`
   - `crafting`, `grid`, `portable`

2. **Enable verbose logging**
   ```properties
   verboseLogging=true
   ```
   Check which blocks are being scanned.

3. **Manual detection**
   If auto-detection fails, the block will still work but without special GUI handling.

---

## Performance Issues

### Issue: Slow startup

**Symptoms:**
- Server takes 30+ seconds to start
- AutoBridge pipeline takes 10+ seconds

**Solutions:**

1. **Enable caching**
   ```properties
   enableCache=true
   ```
   Subsequent starts will be much faster.

2. **Reduce max texture size**
   ```properties
   maxTextureSize=512
   ```
   Smaller textures process faster.

3. **Exclude unnecessary mods**
   - Remove client-only mods from server
   - Use a dedicated mods folder for server

---

### Issue: High memory usage

**Symptoms:**
- Server uses excessive RAM
- OutOfMemoryError

**Solutions:**

1. **Increase Java heap size**
   Edit server startup script:
   ```bash
   java -Xmx4G -Xms2G -jar server.jar
   ```

2. **Reduce cache size**
   ```properties
   cacheExpiryHours=12
   ```

3. **Disable verbose logging**
   ```properties
   verboseLogging=false
   ```

---

## Debugging

### Enable Debug Mode

1. **Enable verbose logging**
   Edit `autobridge.properties`:
   ```properties
   verboseLogging=true
   ```

2. **Check console output**
   Look for:
   - Pipeline phase timing
   - Error stack traces
   - Detailed operation logs

### Check Generated Files

**Location:** `<geyser-data>/`

**Files to check:**
- `custom_mappings/items.json` - Item mappings
- `custom_mappings/blocks.json` - Block mappings
- `generated_textures/` - Extracted textures
- `AutoBridge_Pack.zip` - Generated resource pack
- `autobridge.properties` - Configuration
- `cache/` - Cached scan results

### Validate Generated Files

1. **Check items.json**
   ```bash
   # Should be valid JSON
   cat custom_mappings/items.json | python -m json.tool
   ```

2. **Check blocks.json**
   ```bash
   cat custom_mappings/blocks.json | python -m json.tool
   ```

3. **Check resource pack**
   ```bash
   unzip -l AutoBridge_Pack.zip
   ```
   Should contain:
   - `manifest.json`
   - `texts/en_US.lang`
   - `textures/` directory
   - `item_texture.json`

---

## Getting Help

### Before Asking for Help

1. **Enable verbose logging** and check console
2. **Check this troubleshooting guide**
3. **Review generated files** for errors
4. **Test with minimal mods** (1-2 mods only)

### Providing Information

When asking for help, include:

1. **AutoBridge version**
   ```
   AutoBridge 0.1.0-SNAPSHOT
   ```

2. **Geyser version**
   ```
   Geyser version 2.11.2-SNAPSHOT
   ```

3. **Java version**
   ```bash
   java -version
   ```

4. **Console logs** (with verbose logging enabled)

5. **Configuration** (`autobridge.properties`)

6. **Mod list** (names and versions)

7. **Error messages** (full stack traces)

### Where to Get Help

- **GitHub Issues**: https://github.com/Leafy-Bot/CombindMC/issues
- **Geyser Discord**: https://discord.gg/geysermc
- **Project Documentation**: See README.md, DEVELOPER_GUIDE.md

---

## Known Limitations

See LIMITATIONS.md for detailed information on:
- Geyser/Bedrock hard limitations
- Texture translation constraints
- Mapping coverage limitations
- Performance considerations

---

## Quick Reference

### Configuration File Location
`<geyser-data>/autobridge.properties`

### Log Location
Server console (enable `verboseLogging=true`)

### Generated Files Location
`<geyser-data>/`
- `custom_mappings/` - JSON mappings
- `generated_textures/` - Extracted textures
- `cache/` - Cached data
- `AutoBridge_Pack.zip` - Resource pack

### Common Commands

**Clear cache:**
```bash
rm -rf <geyser-data>/cache
```

**Regenerate textures:**
```bash
rm -rf <geyser-data>/generated_textures
```

**Reset configuration:**
```bash
rm <geyser-data>/autobridge.properties
```

**Check version:**
```bash
# Check extension.yml in the JAR
unzip -p AutoBridge-0.1.0-SNAPSHOT.jar extension.yml
```

---

**Last Updated**: 2026-09-14  
**Version**: 0.1.0-SNAPSHOT
