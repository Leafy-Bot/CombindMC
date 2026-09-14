# Changelog

All notable changes to the AutoBridge project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0-SNAPSHOT] - 2026-09-14

### Added
- **Core Pipeline**
  - ModScanner: JAR-based mod discovery without Minecraft runtime
  - TexturePipeline: Texture extraction and placeholder generation
  - MappingBuilder: Geyser custom_mappings JSON generation
  - PackBuilder: Bedrock resource pack assembly
  - AutoBlockDetector: GUI block detection by name patterns
  - CacheManager: Scan result persistence
  - GuiTranslator: Form description generation for mod GUIs

- **Configuration System**
  - AutoBridgeConfig: Properties-based configuration with auto-save
  - Configurable cache, logging, texture settings
  - Mods directory override support
  - Verbose logging option for debugging

- **Performance Metrics**
  - Timing for each pipeline phase (scan, texture, mapping, pack)
  - Total pipeline time tracking
  - Verbose logging with millisecond precision

- **Testing**
  - TestHarness: 7 unit tests covering all core modules
  - ConfigTest: 3 unit tests for configuration system
  - IntegrationTest: Full pipeline test with real mod JARs
  - Test mod with real textures for realistic testing

- **Documentation**
  - README.md: Complete project overview and usage guide
  - SETUP.md: Server setup instructions
  - LIMITATIONS.md: Known constraints and limitations
  - CHANGELOG.md: This file
  - Configuration documentation with examples
  - Performance metrics documentation

- **Build System**
  - build.bat: One-click build script for Windows
  - Manual build instructions for other platforms
  - Extension JAR packaging with extension.yml

### Technical Details
- Compiled against Geyser API 2.11.2-SNAPSHOT (verified via javap)
- Java 25 compatibility
- All API calls verified against actual Geyser source code
- Zero manual configuration required for basic usage
- Supports NeoForge and Fabric mods
- AE2 pattern matching for GUI detection

### Test Results
- 7/7 unit tests passing
- 3/3 config tests passing
- Integration test: 4 mod JARs scanned, 3 items + 3 blocks discovered
- Resource pack generation: 965 bytes with real content

## [Unreleased]

### Planned
- Runtime command system for server admins
- Hot-reload support without server restart
- Support for more mod loaders (Quilt, etc.)
- Advanced texture format support (animated textures, etc.)
- Performance profiling and optimization
- Developer API for custom integrations

---

## Version History

- **0.1.0-SNAPSHOT** - Initial release with full pipeline, config system, and comprehensive testing
