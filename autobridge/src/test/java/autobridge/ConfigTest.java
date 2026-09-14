package autobridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests for AutoBridgeConfig.
 */
public class ConfigTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== AutoBridge Config Tests ===\n");
        
        int passed = 0;
        int failed = 0;
        
        // Test 1: Default config creation
        System.out.println("[TEST 1] Default config creation...");
        try {
            Path tempDir = Files.createTempDirectory("autobridge-config-test-");
            AutoBridgeConfig config = new AutoBridgeConfig(tempDir);
            
            if (!config.isEnableCache()) throw new RuntimeException("enableCache should default to true");
            if (!config.isGeneratePlaceholders()) throw new RuntimeException("generatePlaceholders should default to true");
            if (config.getMaxTextureSize() != 1024) throw new RuntimeException("maxTextureSize should default to 1024");
            if (config.isVerboseLogging()) throw new RuntimeException("verboseLogging should default to false");
            if (!config.isAutoDetectGuiBlocks()) throw new RuntimeException("autoDetectGuiBlocks should default to true");
            if (config.getCacheExpiryHours() != 24) throw new RuntimeException("cacheExpiryHours should default to 24");
            if (!config.getModsDirectoryOverride().isEmpty()) throw new RuntimeException("modsDirectoryOverride should default to empty");
            
            // Check that config file was created
            Path configFile = tempDir.resolve("autobridge.properties");
            if (!Files.exists(configFile)) throw new RuntimeException("Config file should be created");
            
            System.out.println("  PASSED — default config created correctly");
            passed++;
            
            // Cleanup
            Files.deleteIfExists(configFile);
            Files.deleteIfExists(tempDir);
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }
        
        // Test 2: Config modification and persistence
        System.out.println("[TEST 2] Config modification and persistence...");
        try {
            Path tempDir = Files.createTempDirectory("autobridge-config-test-");
            AutoBridgeConfig config = new AutoBridgeConfig(tempDir);
            
            // Modify settings
            config.setEnableCache(false);
            config.setVerboseLogging(true);
            config.setMaxTextureSize(2048);
            config.setModsDirectoryOverride("C:/custom/mods");
            
            // Create new config instance to test persistence
            AutoBridgeConfig config2 = new AutoBridgeConfig(tempDir);
            
            if (config2.isEnableCache()) throw new RuntimeException("enableCache should be false after reload");
            if (!config2.isVerboseLogging()) throw new RuntimeException("verboseLogging should be true after reload");
            if (config2.getMaxTextureSize() != 2048) throw new RuntimeException("maxTextureSize should be 2048 after reload");
            if (!config2.getModsDirectoryOverride().equals("C:/custom/mods")) throw new RuntimeException("modsDirectoryOverride should persist");
            
            System.out.println("  PASSED — config modifications persist correctly");
            passed++;
            
            // Cleanup
            Files.deleteIfExists(tempDir.resolve("autobridge.properties"));
            Files.deleteIfExists(tempDir);
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }
        
        // Test 3: Config file corruption handling
        System.out.println("[TEST 3] Config file corruption handling...");
        try {
            Path tempDir = Files.createTempDirectory("autobridge-config-test-");
            Path configFile = tempDir.resolve("autobridge.properties");
            
            // Write invalid config
            Files.writeString(configFile, "this is not valid properties format\n===invalid===");
            
            // Should load with defaults
            AutoBridgeConfig config = new AutoBridgeConfig(tempDir);
            
            if (!config.isEnableCache()) throw new RuntimeException("Should fall back to defaults on corruption");
            
            System.out.println("  PASSED — handles corrupted config gracefully");
            passed++;
            
            // Cleanup
            Files.deleteIfExists(configFile);
            Files.deleteIfExists(tempDir);
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }
        
        // Summary
        System.out.println("\n=== Results: " + passed + "/" + (passed + failed) + " tests passed ===");
        
        if (failed > 0) {
            System.exit(1);
        }
    }
}
