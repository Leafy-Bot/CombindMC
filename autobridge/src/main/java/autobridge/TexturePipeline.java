package autobridge;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * TexturePipeline — Extracts textures from mod JARs and converts to Bedrock format.
 * No Minecraft runtime required.
 */
public class TexturePipeline {

    private final Path outputDir;
    private final Map<String, String> textureMap = new HashMap<>();

    public TexturePipeline(Path outputDir) {
        this.outputDir = outputDir;
        try { Files.createDirectories(outputDir); } catch (IOException ignored) {}
    }

    public boolean processItemTexture(ModScanner.ModItem item, Path modsDir) {
        String bedrockName = item.bedrockId().replace(':', '_');
        String textureKey = item.javaId();

        if (textureMap.containsKey(textureKey)) return true;

        // Try to extract texture from the mod JAR
        for (ModScanner.TextureSource tex : item.textures()) {
            if (tex.sourcePath() != null && Files.exists(tex.sourcePath())) {
                try {
                    Path target = outputDir.resolve("items").resolve(bedrockName + ".png");
                    Files.createDirectories(target.getParent());
                    Files.copy(tex.sourcePath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    textureMap.put(textureKey, "textures/items/" + bedrockName + ".png");
                    return true;
                } catch (IOException e) {
                    // fall through to placeholder
                }
            }
        }

        // Try extracting directly from the JAR
        Path extracted = extractFromJar(modsDir, item.namespace(), item.path(), "items");
        if (extracted != null) {
            try {
                Path target = outputDir.resolve("items").resolve(bedrockName + ".png");
                Files.createDirectories(target.getParent());
                Files.copy(extracted, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                textureMap.put(textureKey, "textures/items/" + bedrockName + ".png");
                return true;
            } catch (IOException e) {
                // fall through
            }
        }

        // Generate placeholder
        try {
            Path target = outputDir.resolve("items").resolve(bedrockName + ".png");
            Files.createDirectories(target.getParent());
            generatePlaceholder(target, bedrockName, item.namespace());
            textureMap.put(textureKey, "textures/items/" + bedrockName + ".png");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean processBlockTexture(ModScanner.ModBlock block, Path modsDir) {
        String bedrockName = block.bedrockId().replace(':', '_');
        String textureKey = block.javaId();

        if (textureMap.containsKey(textureKey)) return true;

        // Try JAR extraction
        Path extracted = extractFromJar(modsDir, block.namespace(), block.path(), "block");
        if (extracted != null) {
            try {
                Path target = outputDir.resolve("blocks").resolve(bedrockName + ".png");
                Files.createDirectories(target.getParent());
                Files.copy(extracted, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                textureMap.put(textureKey, "textures/blocks/" + bedrockName + ".png");
                return true;
            } catch (IOException e) {
                // fall through
            }
        }

        // Generate placeholder
        try {
            Path target = outputDir.resolve("blocks").resolve(bedrockName + ".png");
            Files.createDirectories(target.getParent());
            generatePlaceholder(target, bedrockName, block.namespace());
            textureMap.put(textureKey, "textures/blocks/" + bedrockName + ".png");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Extract a texture from a mod JAR by searching for the texture path.
     */
    private Path extractFromJar(Path modsDir, String namespace, String name, String textureDir) {
        if (!Files.exists(modsDir)) return null;

        try (var stream = Files.list(modsDir)) {
            for (Path jarPath : stream.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (JarFile jar = new JarFile(jarPath.toFile())) {
                    String[] candidates = {
                        "assets/" + namespace + "/textures/" + textureDir + "/" + name + ".png",
                        "assets/" + namespace + "/textures/" + textureDir + "s/" + name + ".png"
                    };
                    for (String candidate : candidates) {
                        JarEntry entry = jar.getJarEntry(candidate);
                        if (entry != null && !entry.isDirectory()) {
                            Path tempFile = outputDir.resolve("_temp_extract.png");
                            try (InputStream is = jar.getInputStream(entry)) {
                                Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                            return tempFile;
                        }
                    }
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
        return null;
    }

    /**
     * Generate a colored placeholder texture.
     */
    private void generatePlaceholder(Path target, String name, String namespace) throws IOException {
        int hash = Math.abs(namespace.hashCode()) % 6;
        Color[] colors = {
            new Color(200, 50, 50), new Color(50, 150, 50), new Color(50, 100, 200),
            new Color(200, 150, 50), new Color(150, 50, 200), new Color(50, 180, 180)
        };
        Color color = colors[hash];

        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 16, 16);
        g.setColor(color.darker());
        g.setStroke(new BasicStroke(1));
        g.drawRect(0, 0, 15, 15);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        String letter = name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase();
        FontMetrics fm = g.getFontMetrics();
        g.drawString(letter, (16 - fm.stringWidth(letter)) / 2, (16 - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();

        javax.imageio.ImageIO.write(img, "PNG", target.toFile());
    }

    public Path generateItemTextureJson(Path packOutputDir) throws IOException {
        Path textureJson = packOutputDir.resolve("item_texture.json");
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"geometry\": {\n");
        boolean first = true;
        for (Map.Entry<String, String> entry : textureMap.entrySet()) {
            if (!first) json.append(",\n");
            first = false;
            String bedrockName = entry.getValue().replace("textures/", "").replace(".png", "");
            json.append("    \"").append(bedrockName).append("\": \"").append(entry.getValue()).append("\"");
        }
        json.append("\n  },\n  \"description\": {\n    \"width\": 16,\n    \"height\": 16\n  }\n}");
        Files.writeString(textureJson, json.toString());
        return textureJson;
    }

    public Map<String, String> getTextureMap() { return Map.copyOf(textureMap); }
}
