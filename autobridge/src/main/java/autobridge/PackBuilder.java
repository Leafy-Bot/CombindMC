package autobridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PackBuilder — Fully automatic Bedrock resource pack generation.
 *
 * Assembles all processed textures, mappings, manifests, and language files
 * into a valid Bedrock resource pack. Zero manual config.
 */
public class PackBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger("PackBuilder");

    private final AutoBridge bridge;
    private int packCount = 0;
    private Path lastGeneratedPack;

    // Cached language data populated during scan
    private List<ModScanner.ModItem> cachedItems;
    private List<ModScanner.ModBlock> cachedBlocks;

    public PackBuilder(AutoBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * Set cached scan data so the pack builder can include localized names.
     * Called from AutoBridge after the pipeline scan phase completes.
     */
    public void setScanData(List<ModScanner.ModItem> items, List<ModScanner.ModBlock> blocks) {
        this.cachedItems = items;
        this.cachedBlocks = blocks;
    }

    /**
     * Generate the complete Bedrock resource pack.
     * Creates: manifest.json, texts/language.en_us, item_texture.json,
     * all textures, then zips everything into AutoBridge_Pack.zip.
     */
    public Path generatePack() throws IOException {
        Path outputDir = bridge.extensionsDir().resolve("generated_pack");
        Files.createDirectories(outputDir);

        // Clean previous generation
        if (Files.exists(outputDir)) {
            deleteRecursively(outputDir);
        }
        Files.createDirectories(outputDir);

        // Step 1: manifest.json
        generateManifest(outputDir);

        // Step 2: Language file with ALL discovered item/block names
        generateLanguageFiles(outputDir);

        // Step 3: Texture definitions
        texturePipeline().generateItemTextureJson(outputDir);
        texturePipeline().generateBlocksJson(outputDir);

        // Step 4: Copy processed textures
        copyTextures(outputDir);

        // Step 5: Create zip
        Path zipPath = bridge.extensionsDir().resolve("AutoBridge_Pack.zip");
        createZip(outputDir, zipPath);

        lastGeneratedPack = zipPath;
        packCount++;

        LOGGER.info("AutoBridge/Pack: Generated resource pack → {} ({} total)", zipPath, packCount);
        return zipPath;
    }

    /**
     * Generate manifest.json with proper UUIDs derived from server state.
     */
    private void generateManifest(Path outputDir) throws IOException {
        // Use deterministic UUID based on extension dir path for consistency
        String uuid = bridge.extensionsDir().toAbsolutePath().toString().hashCode();
        String packUuid = Integer.toHexString(uuid).replace("-", "x") + "-pack";

        String json = """
            {
              "format_version": 2,
              "header": {
                "description": "AutoBridge generated resource pack for modded content",
                "name": "AutoBridge Mod Content",
                "uuid": "%uuid%",
                "version": [1, 0, 0],
                "min_game_engine_version": [1, 20, 0]
              },
              "modules": [
                {
                  "type": "resources",
                  "uuid": "%resource_uuid%",
                  "version": [1, 0, 0]
                }
              ],
              "metadata": {
                "authors": ["AutoBridge"]
              }
            }
            """;

        json = json.replace("%uuid%", packUuid)
                   .replace("%resource_uuid%", packUuid + "-resources");

        Files.writeString(outputDir.resolve("manifest.json"), json);
        LOGGER.debug("AutoBridge/Pack: Generated manifest.json");
    }

    /**
     * Generate language.en_us.lang with ALL discovered item and block names.
     * Populated from the scanner's cached data — fully automatic.
     */
    private void generateLanguageFiles(Path outputDir) throws IOException {
        Path textsDir = outputDir.resolve("texts");
        Files.createDirectories(textsDir);

        Path langFile = textsDir.resolve("en_us.lang");

        StringBuilder lang = new StringBuilder();
        lang.append("# AutoBridge generated language file\n");
        lang.append("# Format: <identifier>=<display name>\n");
        lang.append("# Auto-generated — do not edit manually\n\n");

        // Add all item names
        if (cachedItems != null) {
            for (ModScanner.ModItem item : cachedItems) {
                lang.append("item.").append(item.javaId().getNamespace()).append(".")
                    .append(item.javaId().getPath()).append("=")
                    .append(item.displayName()).append("\n");
            }
        }

        // Add all block names
        if (cachedBlocks != null) {
            for (ModScanner.ModBlock block : cachedBlocks) {
                lang.append("block.").append(block.javaId().getNamespace()).append(".")
                    .append(block.javaId().getPath()).append("=")
                    .append(block.displayName()).append("\n");
            }
        }

        Files.writeString(langFile, lang.toString());

        // Also save to Geyser's locales directory for override
        Path geyserLocales = bridge.extensionsDir().resolve("locales").resolve("overrides");
        Files.createDirectories(geyserLocales);
        Files.copy(langFile, geyserLocales.resolve("en_us.lang"),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        int itemCount = cachedItems != null ? cachedItems.size() : 0;
        int blockCount = cachedBlocks != null ? cachedBlocks.size() : 0;
        LOGGER.info("AutoBridge/Pack: Generated language file with {} item names + {} block names",
            itemCount, blockCount);
    }

    /**
     * Copy all processed textures into the resource pack directory.
     */
    private void copyTextures(Path outputDir) throws IOException {
        Path srcItems = bridge.extensionsDir().resolve("generated_textures").resolve("items");
        Path srcBlocks = bridge.extensionsDir().resolve("generated_textures").resolve("blocks");
        Path dstItems = outputDir.resolve("textures").resolve("items");
        Path dstBlocks = outputDir.resolve("textures").resolve("blocks");

        if (Files.exists(srcItems)) {
            Files.createDirectories(dstItems);
            copyDirectory(srcItems, dstItems);
        }

        if (Files.exists(srcBlocks)) {
            Files.createDirectories(dstBlocks);
            Files.createDirectories(outputDir.resolve("textures"));
            copyDirectory(srcBlocks, dstBlocks);
        }
    }

    /**
     * Create a zip file from the pack directory.
     */
    private void createZip(Path sourceDir, Path zipPath) throws IOException {
        // Remove old zip if exists
        if (Files.exists(zipPath)) {
            Files.delete(zipPath);
        }

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String entryName = sourceDir.relativize(path).toString();
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        LOGGER.warn("Failed to add entry to pack: {}", path);
                    }
                });
        }

        long sizeKB = Files.size(zipPath) / 1024;
        LOGGER.debug("AutoBridge/Pack: Created zip at {} ({} KB)", zipPath, sizeKB);
    }

    /**
     * Recursively copy a directory tree.
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(target)) {
            Files.createDirectories(target);
        }

        try (var stream = Files.newDirectoryStream(source)) {
            for (Path child : stream) {
                Path dest = target.resolve(child.getFileName());
                if (Files.isDirectory(child)) {
                    copyDirectory(child, dest);
                } else {
                    Files.copy(child, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * Recursively delete a directory tree.
     */
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> {
                    try { deleteRecursively(p); } catch (IOException ignored) {}
                });
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * Returns the number of packs generated so far.
     */
    public int getPackCount() {
        return packCount;
    }

    /**
     * Returns the path to the last generated pack.
     */
    public Path getLastGeneratedPack() {
        return lastGeneratedPack;
    }

    /**
     * Accessor for TexturePipeline.
     */
    private TexturePipeline texturePipeline() {
        return bridge.texturePipeline();
    }
}
