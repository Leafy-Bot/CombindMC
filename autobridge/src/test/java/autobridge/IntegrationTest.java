package autobridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Integration test — runs the full pipeline against real mod JARs.
 */
public class IntegrationTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== AutoBridge Integration Test ===\n");

        Path testModsDir = Path.of("test-mods");
        if (!Files.exists(testModsDir)) {
            System.out.println("ERROR: test-mods/ directory not found. Copy real mod JARs there first.");
            System.exit(1);
        }

        long fileCount = Files.list(testModsDir).filter(p -> p.toString().endsWith(".jar")).count();
        System.out.println("Testing with " + fileCount + " real mod JARs in " + testModsDir.toAbsolutePath() + "\n");

        // Phase 1: Scan
        System.out.println("[Phase 1] Scanning mods...");
        ModScanner scanner = new ModScanner(testModsDir);
        ModScanner.ScanResult result = scanner.scan();
        System.out.println("  Found " + result.mods().size() + " mods, " + result.items().size() + " items, " + result.blocks().size() + " blocks\n");

        if (result.mods().isEmpty()) {
            System.out.println("WARNING: No mods discovered. Check that JARs have valid fabric.mod.json or neoforge.mods.toml");
            System.exit(1);
        }

        // Print discovered mods
        System.out.println("Discovered mods:");
        for (ModScanner.ModInfo mod : result.mods()) {
            System.out.println("  - " + mod.name() + " (" + mod.namespace() + " v" + mod.version() + ")");
        }
        System.out.println();

        // Phase 2: Textures
        System.out.println("[Phase 2] Processing textures...");
        Path tempDir = Files.createTempDirectory("autobridge-integration-");
        Path texDir = tempDir.resolve("textures");
        TexturePipeline pipeline = new TexturePipeline(texDir);

        int itemsOk = 0, blocksOk = 0;
        for (ModScanner.ModItem item : result.items()) {
            if (pipeline.processItemTexture(item, testModsDir)) itemsOk++;
        }
        for (ModScanner.ModBlock block : result.blocks()) {
            if (pipeline.processBlockTexture(block, testModsDir)) blocksOk++;
        }
        System.out.println("  Textures: " + itemsOk + " items, " + blocksOk + " blocks processed\n");

        // Phase 3: Mappings
        System.out.println("[Phase 3] Generating mappings...");
        Path mapDir = tempDir.resolve("mappings");
        MappingBuilder mapper = new MappingBuilder(mapDir);
        Path itemsJson = mapper.generateItemsJson(result.items());
        Path blocksJson = mapper.generateBlocksJson(result.blocks());
        System.out.println("  items.json: " + Files.size(itemsJson) + " bytes");
        System.out.println("  blocks.json: " + Files.size(blocksJson) + " bytes");
        System.out.println("  Total mappings: " + mapper.getMappingCount() + "\n");

        // Phase 4: Pack
        System.out.println("[Phase 4] Building resource pack...");
        Path packDir = tempDir.resolve("pack");
        PackBuilder packBuilder = new PackBuilder(packDir);
        Path zip = packBuilder.generatePack(pipeline, result.items(), result.blocks());
        long packBytes = Files.size(zip);
        String packSizeStr = packBytes < 1024 ? packBytes + " bytes" : (packBytes / 1024) + " KB";
        System.out.println("  Pack: " + zip + " (" + packSizeStr + ")\n");

        // Phase 5: GUI Detection
        System.out.println("[Phase 5] Detecting GUI blocks...");
        AutoBlockDetector detector = new AutoBlockDetector();
        var guiMap = detector.detectGuiBlocks(result.blocks());
        System.out.println("  GUI blocks found: " + guiMap.size());
        for (var entry : guiMap.entrySet()) {
            System.out.println("    " + entry.getKey() + " → " + entry.getValue());
        }
        System.out.println();

        // Phase 6: Cache
        System.out.println("[Phase 6] Testing cache...");
        Path cacheDir = tempDir.resolve("cache");
        CacheManager cache = new CacheManager(cacheDir);
        for (ModScanner.ModInfo mod : result.mods()) {
            cache.registerModHash(mod.namespace(), mod.version());
        }
        cache.saveCache(result.items(), result.blocks());
        var loaded = cache.loadCache();
        System.out.println("  Cache valid: " + (loaded != null && loaded.isValid()) + "\n");

        // Summary
        System.out.println("=== Integration Test Complete ===");
        System.out.println("Mods: " + result.mods().size());
        System.out.println("Items: " + result.items().size());
        System.out.println("Blocks: " + result.blocks().size());
        System.out.println("Textures: " + (itemsOk + blocksOk));
        System.out.println("Mappings: " + mapper.getMappingCount());
        System.out.println("GUI blocks: " + guiMap.size());
        long finalPackBytes = Files.size(zip);
        String finalPackSizeStr = finalPackBytes < 1024 ? finalPackBytes + " bytes" : (finalPackBytes / 1024) + " KB";
        System.out.println("Pack size: " + finalPackSizeStr);

        // Cleanup
        deleteRecursively(tempDir);
        System.out.println("\nCleaned up temp directory.");
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    stream.forEach(p -> deleteRecursively(p));
                }
            }
            Files.deleteIfExists(path);
        } catch (Exception ignored) {}
    }
}
