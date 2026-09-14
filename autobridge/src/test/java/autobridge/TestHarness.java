package autobridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Standalone test harness — runs the full pipeline without a Minecraft server.
 * Validates: scanner, textures, mappings, pack, cache, GUI detection.
 */
public class TestHarness {

    public static void main(String[] args) throws Exception {
        System.out.println("=== AutoBridge Test Harness ===\n");

        Path tempDir = Files.createTempDirectory("autobridge-test-");
        System.out.println("Test directory: " + tempDir + "\n");

        int passed = 0, failed = 0;

        // Test 1: ModScanner
        System.out.println("[TEST 1] ModScanner...");
        try {
            Path modsDir = tempDir.resolve("mods");
            Files.createDirectories(modsDir);
            ModScanner scanner = new ModScanner(modsDir);
            ModScanner.ScanResult result = scanner.scan();
            // With empty mods dir, should return empty results
            if (result.items() == null || result.blocks() == null || result.mods() == null) {
                throw new RuntimeException("Null results from empty scan");
            }
            System.out.println("  PASSED — scan returned: " + result.items().size() + " items, "
                + result.blocks().size() + " blocks, " + result.mods().size() + " mods");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // Test 2: TexturePipeline
        System.out.println("[TEST 2] TexturePipeline...");
        try {
            Path texDir = tempDir.resolve("textures");
            TexturePipeline pipeline = new TexturePipeline(texDir);

            // Create a test item with no textures → should generate placeholder
            var testItem = new ModScanner.ModItem("testmod:test_item", "Test Item", 500, 64, false,
                ModScanner.GeometryType.MODEL_2D, List.of());

            boolean ok = pipeline.processItemTexture(testItem, tempDir.resolve("mods"));
            if (!ok) throw new RuntimeException("Failed to process item texture");

            var texMap = pipeline.getTextureMap();
            if (texMap.isEmpty()) throw new RuntimeException("No textures in map");

            // Verify placeholder was generated
            Path placeholder = texDir.resolve("items").resolve("testmod_test_item_ab_item.png");
            if (!Files.exists(placeholder)) throw new RuntimeException("Placeholder texture not created");

            long size = Files.size(placeholder);
            if (size == 0) throw new RuntimeException("Placeholder texture is empty");

            System.out.println("  PASSED — " + texMap.size() + " textures, placeholder " + size + " bytes");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // Test 3: MappingBuilder
        System.out.println("[TEST 3] MappingBuilder...");
        try {
            Path mapDir = tempDir.resolve("mappings");
            MappingBuilder builder = new MappingBuilder(mapDir);

            var items = List.of(
                new ModScanner.ModItem("testmod:item_a", "Item A", 500, 64, false,
                    ModScanner.GeometryType.MODEL_2D, List.of()),
                new ModScanner.ModItem("testmod:item_b", "Item B", 501, 1, true,
                    ModScanner.GeometryType.CONSUMABLE, List.of())
            );
            var blocks = List.of(
                new ModScanner.ModBlock("testmod:block_a", "Block A", 0, 0.6f,
                    ModScanner.GeometryType.CUBE, List.of(), 1),
                new ModScanner.ModBlock("testmod:block_b", "Block B", 7, 0.6f,
                    ModScanner.GeometryType.COMPLEX, List.of(), 4)
            );

            Path itemsJson = builder.generateItemsJson(items);
            Path blocksJson = builder.generateBlocksJson(blocks);

            if (!Files.exists(itemsJson)) throw new RuntimeException("items.json not created");
            if (!Files.exists(blocksJson)) throw new RuntimeException("blocks.json not created");

            String itemsContent = Files.readString(itemsJson);
            String blocksContent = Files.readString(blocksJson);

            if (!itemsContent.contains("\"format_version\": 2")) throw new RuntimeException("items.json missing format_version");
            if (!blocksContent.contains("\"format_version\": 1")) throw new RuntimeException("blocks.json missing format_version");
            if (!itemsContent.contains("testmod:item_a")) throw new RuntimeException("items.json missing test item");
            if (!blocksContent.contains("state_overrides")) throw new RuntimeException("blocks.json missing state_overrides for multi-state block");

            System.out.println("  PASSED — items.json (" + itemsContent.length() + " bytes), blocks.json (" + blocksContent.length() + " bytes)");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // Test 4: PackBuilder
        System.out.println("[TEST 4] PackBuilder...");
        try {
            Path packDir = tempDir.resolve("pack_output");
            Path texDir = tempDir.resolve("textures");
            TexturePipeline pipeline = new TexturePipeline(texDir);

            var items = List.of(
                new ModScanner.ModItem("testmod:pack_item", "Pack Item", 500, 64, false,
                    ModScanner.GeometryType.MODEL_2D, List.of())
            );
            var blocks = List.of(
                new ModScanner.ModBlock("testmod:pack_block", "Pack Block", 0, 0.6f,
                    ModScanner.GeometryType.CUBE, List.of(), 1)
            );

            // Process textures first
            for (var item : items) pipeline.processItemTexture(item, tempDir.resolve("mods"));
            for (var block : blocks) pipeline.processBlockTexture(block, tempDir.resolve("mods"));

            PackBuilder packBuilder = new PackBuilder(packDir);
            Path zip = packBuilder.generatePack(pipeline, items, blocks);

            if (zip == null) throw new RuntimeException("generatePack returned null");
            if (!Files.exists(zip)) throw new RuntimeException("Pack zip not created");

            long sizeKB = Files.size(zip) / 1024;
            System.out.println("  PASSED — AutoBridge_Pack.zip (" + sizeKB + " KB)");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // Test 5: CacheManager
        System.out.println("[TEST 5] CacheManager...");
        try {
            Path cacheDir = tempDir.resolve("cache");
            CacheManager cache = new CacheManager(cacheDir);
            cache.registerModHash("testmod", "abc123");

            var items = List.of(new ModScanner.ModItem("testmod:x", "X", 500, 64, false,
                ModScanner.GeometryType.MODEL_2D, List.of()));
            var blocks = List.<ModScanner.ModBlock>of();

            cache.saveCache(items, blocks);

            // Reload with same hashes
            CacheManager cache2 = new CacheManager(cacheDir);
            cache2.registerModHash("testmod", "abc123");
            var loaded = cache2.loadCache();

            if (loaded == null || !loaded.isValid()) throw new RuntimeException("Cache should be valid");

            // Reload with different hashes → should be invalid
            CacheManager cache3 = new CacheManager(cacheDir);
            cache3.registerModHash("testmod", "different");
            var loaded2 = cache3.loadCache();

            if (loaded2 != null) throw new RuntimeException("Cache should be invalid with different hash");

            System.out.println("  PASSED — cache save/load/invalidation works");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // Test 6: AutoBlockDetector
        System.out.println("[TEST 6] AutoBlockDetector...");
        try {
            var blocks = List.of(
                new ModScanner.ModBlock("appliedenergistics2:me_controller", "ME Controller", 7, 0.6f,
                    ModScanner.GeometryType.COMPLEX, List.of(), 4),
                new ModScanner.ModBlock("appliedenergistics2:me_terminal", "ME Terminal", 0, 0.6f,
                    ModScanner.GeometryType.CUBE, List.of(), 1),
                new ModScanner.ModBlock("testmod:regular_block", "Regular Block", 0, 0.6f,
                    ModScanner.GeometryType.CUBE, List.of(), 1)
            );

            AutoBlockDetector detector = new AutoBlockDetector();
            Map<String, String> guiMap = detector.detectGuiBlocks(blocks);

            if (guiMap.size() < 2) throw new RuntimeException("Expected at least 2 GUI blocks, got " + guiMap.size());

            // Verify AE2 blocks are detected
            boolean foundController = guiMap.values().stream().anyMatch(v -> v.equals("me_terminal"));
            if (!foundController) throw new RuntimeException("ME Controller not detected as GUI block");

            System.out.println("  PASSED — " + guiMap.size() + " GUI blocks detected (AE2 patterns matched)");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // Test 7: GuiTranslator
        System.out.println("[TEST 7] GuiTranslator...");
        try {
            GuiTranslator translator = new GuiTranslator();
            var items = List.of(
                new GuiTranslator.GuiItem("Item 1"),
                new GuiTranslator.GuiItem("Item 2"),
                new GuiTranslator.GuiItem("Item 3")
            );
            var context = new GuiTranslator.GuiContext("me_terminal", items);
            var form = translator.translateGui("me_terminal", context);

            if (form == null) throw new RuntimeException("Form is null");
            if (!form.title().equals("ME Terminal")) throw new RuntimeException("Wrong title: " + form.title());
            if (form.elements().isEmpty()) throw new RuntimeException("Form has no elements");

            System.out.println("  PASSED — " + form);
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // Summary
        System.out.println("\n=== Results: " + passed + "/" + (passed + failed) + " tests passed ===");

        // Cleanup
        deleteRecursively(tempDir);
        System.out.println("Cleaned up test directory.");

        if (failed > 0) System.exit(1);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> { try { deleteRecursively(p); } catch (IOException ignored) {} });
            }
        }
        Files.deleteIfExists(path);
    }
}
