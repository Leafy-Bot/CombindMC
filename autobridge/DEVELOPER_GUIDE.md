# Developer Guide

This guide is for developers who want to contribute to AutoBridge or extend its functionality.

## Project Structure

```
autobridge/
├── src/
│   ├── main/java/autobridge/     # Main source code
│   ├── main/resources/            # Extension metadata
│   └── test/java/autobridge/      # Test code
├── build/                         # Compiled output
├── libs/                          # Geyser API dependencies
├── test-mods/                     # Test mod JARs
└── docs/                          # Documentation
```

## Building from Source

### Prerequisites
- Java 25 or later
- Git

### Build Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/Leafy-Bot/CombindMC.git
   cd CODE/autobridge
   ```

2. **Build using build.bat (Windows)**
   ```bash
   build.bat
   ```

3. **Or build manually**
   ```bash
   # Compile pipeline modules
   javac -d build/classes -source 25 -target 25 \
     src/main/java/autobridge/ModScanner.java \
     src/main/java/autobridge/TexturePipeline.java \
     src/main/java/autobridge/MappingBuilder.java \
     src/main/java/autobridge/PackBuilder.java \
     src/main/java/autobridge/AutoBlockDetector.java \
     src/main/java/autobridge/CacheManager.java \
     src/main/java/autobridge/GuiTranslator.java
   
   # Compile AutoBridge (needs Geyser API)
   javac -cp "build/classes;libs/geyser-api.jar;libs/base-api.jar;libs/events.jar;libs/annotations.jar" \
     -d build/classes -source 25 -target 25 \
     src/main/java/autobridge/AutoBridgeConfig.java \
     src/main/java/autobridge/AutoBridge.java
   
   # Build extension JAR
   # (Use build.bat or manually package classes + extension.yml)
   ```

## Running Tests

### Unit Tests
```bash
java -cp build/classes autobridge.TestHarness
```

### Config Tests
```bash
java -cp build/classes autobridge.ConfigTest
```

### Integration Test
```bash
java -cp build/classes autobridge.IntegrationTest
```

## Code Architecture

### Core Pipeline

The pipeline follows a linear flow:

```
ModScanner → TexturePipeline → MappingBuilder → PackBuilder
```

Each module is independent and can be tested in isolation.

### ModScanner

**Purpose**: Discover items and blocks from mod JARs

**Key Methods**:
- `scan()` - Main entry point, returns ScanResult
- `scanJar(Path jarPath)` - Scans a single JAR
- `readModInfo(JarFile jar)` - Reads mod metadata

**Data Structures**:
- `ModItem` - Represents a discovered item
- `ModBlock` - Represents a discovered block
- `ModInfo` - Mod metadata (namespace, name, version)

**Extension Points**:
- Add support for more mod loaders by implementing new `readModInfo` variants
- Customize item/block discovery by modifying `scanJar`

### TexturePipeline

**Purpose**: Extract and convert textures

**Key Methods**:
- `processItemTexture(ModItem item, Path modsDir)` - Process item texture
- `processBlockTexture(ModBlock block, Path modsDir)` - Process block texture
- `extractFromJar(...)` - Extract texture from JAR
- `generatePlaceholder(...)` - Generate placeholder texture

**Extension Points**:
- Add support for more texture formats (animated, etc.)
- Customize placeholder generation
- Add texture optimization/compression

### MappingBuilder

**Purpose**: Generate Geyser custom_mappings JSON

**Key Methods**:
- `generateItemsJson(List<ModItem> items)` - Generate items.json
- `generateBlocksJson(List<ModBlock> blocks)` - Generate blocks.json

**Extension Points**:
- Add support for more complex block states
- Customize JSON format
- Add validation for generated mappings

### PackBuilder

**Purpose**: Assemble Bedrock resource pack

**Key Methods**:
- `generatePack(...)` - Main entry point
- `generateManifest(...)` - Create pack manifest
- `generateLanguageFiles(...)` - Create language files

**Extension Points**:
- Add more pack metadata
- Customize pack structure
- Add pack validation

### AutoBlockDetector

**Purpose**: Detect blocks with GUIs

**Key Methods**:
- `detectGuiBlocks(List<ModBlock> blocks)` - Main detection
- `detectGuiType(ModBlock block)` - Determine GUI type

**Extension Points**:
- Add more GUI patterns (currently AE2-focused)
- Add support for custom GUI detection rules
- Integrate with GuiTranslator for custom forms

## Adding New Features

### Example: Adding Support for a New Mod Loader

1. **Extend ModScanner**
   ```java
   private ModInfo readNewLoaderModInfo(JarFile jar, JarEntry entry) {
       // Parse new loader's metadata format
       // Return ModInfo with namespace, name, version
   }
   ```

2. **Update readModInfo**
   ```java
   private ModInfo readModInfo(JarFile jar) {
       // Try existing loaders first
       // ...
       
       // Try new loader
       JarEntry newEntry = jar.getJarEntry("newloader.mod.json");
       if (newEntry != null) {
           return readNewLoaderModInfo(jar, newEntry);
       }
       
       return null;
   }
   ```

3. **Add Tests**
   - Create test JAR with new loader format
   - Add to test-mods/
   - Update IntegrationTest to verify

### Example: Adding a New GUI Pattern

1. **Extend AutoBlockDetector**
   ```java
   private static final Map<String, String> NEW_MOD_PATTERNS = Map.of(
       "new_mod_machine", "machine_gui",
       "new_mod_terminal", "terminal_gui"
   );
   
   private String detectGuiType(ModBlock block) {
       // Check existing patterns
       // ...
       
       // Check new mod patterns
       String newPattern = NEW_MOD_PATTERNS.get(block.path());
       if (newPattern != null) return newPattern;
       
       return null;
   }
   ```

2. **Extend GuiTranslator**
   ```java
   public Form translateGui(String guiType, GuiContext context) {
       return switch (guiType.toLowerCase()) {
           // Existing cases
           // ...
           
           // New case
           case "machine_gui" -> buildMachineForm(context);
           default -> buildSimpleListForm(guiType, context);
       };
   }
   
   private Form buildMachineForm(GuiContext context) {
       // Build form for machine GUI
       // ...
   }
   ```

3. **Add Tests**
   - Add test block with new pattern
   - Verify detection in AutoBlockDetector test
   - Verify form generation in GuiTranslator test

## Debugging

### Enable Verbose Logging

Edit `autobridge.properties`:
```properties
verboseLogging=true
```

This will show:
- Detailed timing for each phase
- Stack traces on errors
- Additional diagnostic information

### Common Issues

**Issue**: "Mods directory does not exist"
- **Cause**: Mods directory not found
- **Solution**: Set `modsDirectoryOverride` in config or ensure mods/ exists

**Issue**: "Failed to register item"
- **Cause**: Geyser API error
- **Solution**: Check verbose logs, verify Geyser API version compatibility

**Issue**: "Texture extraction failed"
- **Cause**: Texture not found in JAR
- **Solution**: Check mod JAR structure, verify texture paths

### Using the Test Harness

The test harness creates a temporary directory and runs each module in isolation:

```bash
java -cp build/classes autobridge.TestHarness
```

Output shows:
- Which tests passed/failed
- Error messages for failures
- Generated file sizes

## Performance Profiling

### Built-in Metrics

Enable `verboseLogging=true` to see timing for each phase.

### Manual Profiling

Add timing code:
```java
long start = System.currentTimeMillis();
// ... code to profile ...
long elapsed = System.currentTimeMillis() - start;
logger().info("Operation took " + elapsed + "ms");
```

### Optimization Tips

1. **Cache aggressively** - Use CacheManager for scan results
2. **Batch operations** - Process multiple items/blocks together
3. **Lazy loading** - Only load what's needed
4. **Parallel processing** - Use Java 25 virtual threads for I/O

## Testing Guidelines

### Unit Tests

- Test each module in isolation
- Use temporary directories for file operations
- Clean up after tests
- Test both success and failure cases

### Integration Tests

- Use real mod JARs when possible
- Test full pipeline end-to-end
- Verify generated files are valid
- Test with various mod configurations

### Test Data

- Keep test JARs small and focused
- Include both items and blocks
- Include textures for realistic testing
- Document test data purpose

## Code Style

### Naming Conventions

- **Classes**: PascalCase (e.g., `ModScanner`)
- **Methods**: camelCase (e.g., `scanJar`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `VANILLA_NAMESPACE`)
- **Packages**: lowercase (e.g., `autobridge`)

### Documentation

- Document all public methods
- Include @param, @return, @throws tags
- Add examples for complex methods
- Keep comments up-to-date

### Error Handling

- Use try-catch for I/O operations
- Log errors with context
- Provide meaningful error messages
- Fail gracefully when possible

## Contributing

### Pull Request Process

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add/update tests
5. Run all tests
6. Update documentation
7. Submit pull request

### Code Review

- All changes must be reviewed
- Tests must pass
- Documentation must be updated
- No breaking changes without discussion

### Commit Messages

Format:
```
type: short description

Longer description if needed.

- Bullet points for multiple changes
- Reference issues if applicable
```

Types:
- `feat` - New feature
- `fix` - Bug fix
- `docs` - Documentation
- `test` - Tests
- `refactor` - Code refactoring
- `build` - Build system changes

## Resources

- **Geyser API Documentation**: https://github.com/GeyserMC/Geyser
- **Java 25 Documentation**: https://docs.oracle.com/en/java/
- **Project Repository**: https://github.com/Leafy-Bot/CombindMC

## Getting Help

- Check existing documentation
- Review test code for examples
- Open an issue on GitHub
- Join the discussion

---

**Last Updated**: 2026-09-14  
**Version**: 0.1.0-SNAPSHOT
