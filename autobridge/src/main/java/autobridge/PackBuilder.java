package autobridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PackBuilder — Assembles generated textures and mappings into a Bedrock resource pack.
 */
public class PackBuilder {

    private final Path outputDir;
    private int packCount = 0;
    private Path lastGeneratedPack;

    public PackBuilder(Path outputDir) {
        this.outputDir = outputDir;
    }

    public Path generatePack(TexturePipeline texturePipeline,
                             List<ModScanner.ModItem> items,
                             List<ModScanner.ModBlock> blocks) throws IOException {
        Path packDir = outputDir.resolve("generated_pack");
        if (Files.exists(packDir)) deleteRecursively(packDir);
        Files.createDirectories(packDir);

        // manifest.json
        String uuid = UUID.nameUUIDFromBytes(outputDir.toString().getBytes()).toString();
        String manifest = """
            {
              "format_version": 2,
              "header": {
                "description": "AutoBridge generated resource pack",
                "name": "AutoBridge Mod Content",
                "uuid": "%uuid%",
                "version": [1, 0, 0],
                "min_game_engine_version": [1, 20, 0]
              },
              "modules": [{ "type": "resources", "uuid": "%ruuid%", "version": [1, 0, 0] }],
              "metadata": { "authors": ["AutoBridge"] }
            }
            """.replace("%uuid%", uuid).replace("%ruuid%", uuid + "-res");
        Files.writeString(packDir.resolve("manifest.json"), manifest);

        // Language file
        Path textsDir = packDir.resolve("texts");
        Files.createDirectories(textsDir);
        StringBuilder lang = new StringBuilder("# AutoBridge generated language file\n");
        for (ModScanner.ModItem item : items) {
            lang.append("item.").append(item.javaId().replace(':', '.')).append("=").append(item.displayName()).append("\n");
        }
        for (ModScanner.ModBlock block : blocks) {
            lang.append("block.").append(block.javaId().replace(':', '.')).append("=").append(block.displayName()).append("\n");
        }
        Files.writeString(textsDir.resolve("en_US.lang"), lang.toString());

        // Texture definitions
        texturePipeline.generateItemTextureJson(packDir);

        // Copy textures
        Path srcItems = outputDir.resolve("generated_textures").resolve("items");
        Path srcBlocks = outputDir.resolve("generated_textures").resolve("blocks");
        if (Files.exists(srcItems)) copyDir(srcItems, packDir.resolve("textures").resolve("items"));
        if (Files.exists(srcBlocks)) copyDir(srcBlocks, packDir.resolve("textures").resolve("blocks"));

        // Zip it
        Path zipPath = outputDir.resolve("AutoBridge_Pack.zip");
        if (Files.exists(zipPath)) Files.delete(zipPath);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(packDir).filter(Files::isRegularFile).forEach(path -> {
                try {
                    String entryName = packDir.relativize(path).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException ignored) {}
            });
        }

        lastGeneratedPack = zipPath;
        packCount++;
        long sizeBytes = Files.size(zipPath);
        String sizeStr = sizeBytes < 1024 ? sizeBytes + " bytes" : (sizeBytes / 1024) + " KB";
        System.out.println("[PackBuilder] Generated pack: " + zipPath + " (" + sizeStr + ")");
        return zipPath;
    }

    private void copyDir(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var stream = Files.newDirectoryStream(source)) {
            for (Path child : stream) {
                Path dest = target.resolve(child.getFileName());
                if (Files.isDirectory(child)) copyDir(child, dest);
                else Files.copy(child, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> { try { deleteRecursively(p); } catch (IOException ignored) {} });
            }
        }
        Files.deleteIfExists(path);
    }

    public int getPackCount() { return packCount; }
    public Path getLastGeneratedPack() { return lastGeneratedPack; }
}
