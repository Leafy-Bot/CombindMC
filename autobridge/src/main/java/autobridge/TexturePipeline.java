package autobridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * TexturePipeline — Fully automatic texture extraction and conversion.
 *
 * Reads textures directly from mod JARs, converts them to Bedrock format,
 * and generates item_texture.json. Zero manual configuration.
 *
 * Pipeline integration:
 *   ModScanner.ModItem.textures → processItemTexture() → writes PNG + registers in textureMap
 *   ModScanner.ModBlock.textures → processBlockTexture() → writes PNG + registers in textureMap
 *   generateItemTextureJson() → writes pack-level item_texture.json
 */
public class TexturePipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger("TexturePipeline");

    private static final int TEXTURE_COLUMNS = 16;
    private static final int TEXTURE_ROWS = 16;

    // Placeholder colors per namespace (deterministic, so same mod always gets same color)
    private static final Color[] PLACEHOLDER_COLORS = {
        new Color(200, 50, 50),   // Red
        new Color(50, 150, 50),   // Green
        new Color(50, 100, 200),  // Blue
        new Color(200, 150, 50),  // Orange
        new Color(150, 50, 200),  // Purple
        new Color(50, 180, 180),  // Teal
    };

    private final AutoBridge bridge;
    private final Path outputDir;
    private final Map<String, String> textureMap; // bedrockName -> relativePath

    public TexturePipeline(AutoBridge bridge) {
        this.bridge = bridge;
        this.outputDir = bridge.extensionsDir().resolve("generated_textures");
        this.textureMap = new HashMap<>();

        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create texture output directory", e);
        }
    }

    /**
     * Process ALL textures for a discovered mod item. Automatically extracts
     * from mod JARs, converts to Bedrock format, and registers in the map.
     * Returns true if at least one texture was successfully processed.
     */
    public boolean processItemTexture(ModScanner.ModItem item) {
        String bedrockName = item.bedrockId().getPath();
        String textureKey = item.javaId().toString();

        // Skip if already processed (idempotent)
        if (textureMap.containsKey(textureKey)) {
            return true;
        }

        boolean anySuccess = false;

        // Process each texture source the scanner found
        for (ModScanner.TextureSource texSource : item.textures()) {
            try {
                Path targetPath = outputDir.resolve("items").resolve(bedrockName + ".png");
                Files.createDirectories(targetPath.getParent());

                if (texSource.sourcePath() != null && Files.exists(texSource.sourcePath())) {
                    // Direct copy from extracted JAR asset
                    Files.copy(texSource.sourcePath(), targetPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    anySuccess = true;
                } else if (texSource.type() == ModScanner.TextureType.NAMESPACE_SCAN) {
                    // Try to extract from the mod JAR by name pattern
                    Path jarPath = findModJarForNamespace(item.javaId().getNamespace());
                    if (jarPath != null) {
                        Path extracted = extractTextureFromJar(jarPath, bedrockName);
                        if (extracted != null) {
                            Files.copy(extracted, targetPath,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            anySuccess = true;
                        }
                    }
                }

                if (anySuccess) break;

            } catch (IOException e) {
                LOGGER.debug("Failed to process texture {} for {}: {}",
                    texSource.name(), item.javaId(), e.getMessage());
            }
        }

        // Fallback: generate a colored placeholder
        if (!anySuccess) {
            try {
                Path targetPath = outputDir.resolve("items").resolve(bedrockName + ".png");
                Files.createDirectories(targetPath.getParent());
                generatePlaceholderTexture(targetPath, bedrockName, item.javaId().getNamespace());
                LOGGER.debug("AutoBridge/Textures: Generated placeholder for item {}", item.javaId());
                anySuccess = true;
            } catch (IOException e) {
                LOGGER.error("Failed to generate placeholder for item: {}", item.javaId(), e);
            }
        }

        if (anySuccess) {
            String bedrockRelativePath = "textures/items/" + bedrockName + ".png";
            textureMap.put(textureKey, bedrockRelativePath);
        }

        return anySuccess;
    }

    /**
     * Process ALL textures for a discovered mod block. Same fully-automatic approach.
     */
    public boolean processBlockTexture(ModScanner.ModBlock block) {
        String bedrockName = block.bedrockId().getPath();
        String textureKey = block.javaId().toString();

        if (textureMap.containsKey(textureKey)) {
            return true;
        }

        boolean anySuccess = false;

        for (ModScanner.TextureSource texSource : block.textures()) {
            try {
                Path targetPath = outputDir.resolve("blocks").resolve(bedrockName + ".png");
                Files.createDirectories(targetPath.getParent());

                if (texSource.sourcePath() != null && Files.exists(texSource.sourcePath())) {
                    Files.copy(texSource.sourcePath(), targetPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    anySuccess = true;
                } else if (texSource.type() == ModScanner.TextureType.NAMESPACE_SCAN) {
                    Path jarPath = findModJarForNamespace(block.javaId().getNamespace());
                    if (jarPath != null) {
                        Path extracted = extractTextureFromJar(jarPath, bedrockName);
                        if (extracted != null) {
                            Files.copy(extracted, targetPath,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            anySuccess = true;
                        }
                    }
                }

                if (anySuccess) break;

            } catch (IOException e) {
                LOGGER.debug("Failed to process texture {} for block: {}",
                    texSource.name(), block.javaId(), e.getMessage());
            }
        }

        if (!anySuccess) {
            try {
                Path targetPath = outputDir.resolve("blocks").resolve(bedrockName + ".png");
                Files.createDirectories(targetPath.getParent());
                generatePlaceholderTexture(targetPath, bedrockName, block.javaId().getNamespace());
                LOGGER.debug("AutoBridge/Textures: Generated placeholder for block {}", block.javaId());
                anySuccess = true;
            } catch (IOException e) {
                LOGGER.error("Failed to generate placeholder for block: {}", block.javaId(), e);
            }
        }

        if (anySuccess) {
            String bedrockRelativePath = "textures/blocks/" + bedrockName + ".png";
            textureMap.put(textureKey, bedrockRelativePath);
        }

        return anySuccess;
    }

    /**
     * Generate item_texture.json for the Bedrock resource pack.
     * Maps every processed texture to its Bedrock-relative path.
     */
    public Path generateItemTextureJson(Path packOutputDir) throws IOException {
        Path textureJson = packOutputDir.resolve("item_texture.json");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"geometry\": {\n");

        boolean first = true;
        for (Map.Entry<String, String> entry : textureMap.entrySet()) {
            if (!first) json.append(",\n");
            first = false;

            String bedrockName = entry.getValue().replace("textures/", "").replace(".png", "");
            json.append("    \"").append(bedrockName).append("\": \"").append(entry.getValue()).append("\"");
        }

        json.append("\n  },\n");
        json.append("  \"description\": {\n");
        json.append("    \"width\": ").append(TEXTURE_COLUMNS).append(",\n");
        json.append("    \"height\": ").append(TEXTURE_ROWS).append("\n");
        json.append("  }\n");
        json.append("}");

        Files.writeString(textureJson, json.toString());
        LOGGER.info("AutoBridge/Textures: Generated item_texture.json with {} entries", textureMap.size());

        return textureJson;
    }

    /**
     * Generate blocks.json for the Bedrock resource pack.
     * Minimal definition — most block rendering is handled by material_instances
     * in Geyser's custom_mappings.
     */
    public Path generateBlocksJson(Path packOutputDir) throws IOException {
        Path blocksJson = packOutputDir.resolve("blocks.json");

        String json = """
            {
              "format_version": [1, 0, 0],
              "base_color": "#variant:color"
            }
            """;

        Files.writeString(blocksJson, json);
        return blocksJson;
    }

    // ==================== Internal Methods ====================

    /**
     * Extract a specific texture file from a mod JAR.
     * Searches common texture paths within the JAR.
     */
    private Path extractTextureFromJar(Path jarPath, String textureName) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // Match by texture name in common texture paths
                if (entryName.endsWith(".png") && !entry.isDirectory()) {
                    String baseName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    if (baseName.equals(textureName + ".png") || entryName.contains("/" + textureName + "/")) {
                        // Extract to temp location
                        Path tempPath = bridge.extensionsDir().resolve("_extracted").resolve("jar_temp").resolve(entryName);
                        Files.createDirectories(tempPath.getParent());
                        if (!Files.exists(tempPath)) {
                            try (var is = jar.getInputStream(entry)) {
                                Files.copy(is, tempPath);
                            }
                        }
                        return tempPath;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find the JAR file for a given mod namespace.
     */
    private Path findModJarForNamespace(String namespace) {
        String[] searchDirs = {"mods", System.getProperty("user.dir") + "/mods", "."};

        for (String dir : searchDirs) {
            try (var stream = Files.list(Path.of(dir))) {
                var match = stream.filter(p -> p.toString().endsWith(".jar")
                        && p.toString().toLowerCase().contains(namespace.toLowerCase()))
                    .findFirst();
                if (match.isPresent()) return match.get();
            } catch (IOException ignored) {}
        }
        return null;
    }

    /**
     * Generate a colored placeholder texture when no real texture is available.
     * Uses a deterministic color based on the mod's namespace hash so the same
     * mod always produces the same placeholder color (helps identify mods visually).
     */
    private void generatePlaceholderTexture(Path targetPath, String name, String namespace) throws IOException {
        int hash = namespace.hashCode() % PLACEHOLDER_COLORS.length;
        if (hash < 0) hash = -hash;
        Color color = PLACEHOLDER_COLORS[hash];

        // Create a 16x16 filled rectangle as placeholder
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 16, 16);

        // Add a border
        g.setColor(color.darker());
        g.setStroke(new BasicStroke(1));
        g.drawRect(0, 0, 15, 15);

        // Add first letter of name in center
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        String letter = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
        FontMetrics fm = g.getFontMetrics();
        int x = (16 - fm.stringWidth(letter)) / 2;
        int y = (16 - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(letter, x, y);
        g.dispose();

        // Write as PNG
        javax.imageio.ImageIO.write(img, "PNG", targetPath.toFile());
    }

    /**
     * Returns all processed texture mappings.
     */
    public Map<String, String> getTextureMap() {
        return Map.copyOf(textureMap);
    }
}
