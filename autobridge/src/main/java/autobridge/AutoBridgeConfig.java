package autobridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration manager for AutoBridge.
 * Handles loading and saving configuration options.
 */
public class AutoBridgeConfig {
    
    private boolean enableCache = true;
    private boolean generatePlaceholders = true;
    private int maxTextureSize = 1024;
    private boolean verboseLogging = false;
    private String modsDirectoryOverride = "";
    private boolean autoDetectGuiBlocks = true;
    private int cacheExpiryHours = 24;
    
    private final Path configFile;
    
    public AutoBridgeConfig(Path dataDir) {
        this.configFile = dataDir.resolve("autobridge.properties");
        load();
    }
    
    /**
     * Load configuration from file.
     */
    public void load() {
        if (!Files.exists(configFile)) {
            save(); // Create default config
            return;
        }
        
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(configFile)) {
            props.load(is);
            
            enableCache = Boolean.parseBoolean(props.getProperty("enableCache", "true"));
            generatePlaceholders = Boolean.parseBoolean(props.getProperty("generatePlaceholders", "true"));
            maxTextureSize = Integer.parseInt(props.getProperty("maxTextureSize", "1024"));
            verboseLogging = Boolean.parseBoolean(props.getProperty("verboseLogging", "false"));
            modsDirectoryOverride = props.getProperty("modsDirectoryOverride", "");
            autoDetectGuiBlocks = Boolean.parseBoolean(props.getProperty("autoDetectGuiBlocks", "true"));
            cacheExpiryHours = Integer.parseInt(props.getProperty("cacheExpiryHours", "24"));
            
        } catch (IOException e) {
            System.err.println("[AutoBridgeConfig] Failed to load config: " + e.getMessage());
        }
    }
    
    /**
     * Save configuration to file.
     */
    public void save() {
        Properties props = new Properties();
        props.setProperty("enableCache", String.valueOf(enableCache));
        props.setProperty("generatePlaceholders", String.valueOf(generatePlaceholders));
        props.setProperty("maxTextureSize", String.valueOf(maxTextureSize));
        props.setProperty("verboseLogging", String.valueOf(verboseLogging));
        props.setProperty("modsDirectoryOverride", modsDirectoryOverride);
        props.setProperty("autoDetectGuiBlocks", String.valueOf(autoDetectGuiBlocks));
        props.setProperty("cacheExpiryHours", String.valueOf(cacheExpiryHours));
        
        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream os = Files.newOutputStream(configFile)) {
                props.store(os, "AutoBridge Configuration");
            }
        } catch (IOException e) {
            System.err.println("[AutoBridgeConfig] Failed to save config: " + e.getMessage());
        }
    }
    
    // Getters
    public boolean isEnableCache() { return enableCache; }
    public boolean isGeneratePlaceholders() { return generatePlaceholders; }
    public int getMaxTextureSize() { return maxTextureSize; }
    public boolean isVerboseLogging() { return verboseLogging; }
    public String getModsDirectoryOverride() { return modsDirectoryOverride; }
    public boolean isAutoDetectGuiBlocks() { return autoDetectGuiBlocks; }
    public int getCacheExpiryHours() { return cacheExpiryHours; }
    
    // Setters
    public void setEnableCache(boolean enableCache) { 
        this.enableCache = enableCache; 
        save();
    }
    public void setGeneratePlaceholders(boolean generatePlaceholders) { 
        this.generatePlaceholders = generatePlaceholders; 
        save();
    }
    public void setMaxTextureSize(int maxTextureSize) { 
        this.maxTextureSize = maxTextureSize; 
        save();
    }
    public void setVerboseLogging(boolean verboseLogging) { 
        this.verboseLogging = verboseLogging; 
        save();
    }
    public void setModsDirectoryOverride(String modsDirectoryOverride) { 
        this.modsDirectoryOverride = modsDirectoryOverride; 
        save();
    }
    public void setAutoDetectGuiBlocks(boolean autoDetectGuiBlocks) { 
        this.autoDetectGuiBlocks = autoDetectGuiBlocks; 
        save();
    }
    public void setCacheExpiryHours(int cacheExpiryHours) { 
        this.cacheExpiryHours = cacheExpiryHours; 
        save();
    }
}
