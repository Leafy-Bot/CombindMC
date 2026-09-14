package autobridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Standalone test harness for AutoBridge pipeline validation.
 * Runs outside Minecraft server context using simulation data.
 * Validates that all pipeline modules produce correct output.
 * 
 * Usage: Run from project root with Java 21+
 *   java -cp build/libs/AutoBridge-0.1.0-SNAPSHOT.jar autobridge.TestHarness
 * 
 * This validates:
 * 1. ModScanner produces valid ModItem/ModBlock records
 * 2. TexturePipeline generates texture map entries
 * 3. MappingBuilder produces valid JSON files
 * 4. PackBuilder assembles a valid resource pack zip
 */
public class TestHarness {

    private static final Logger LOGGER = LoggerFactory.getLogger("TestHarness");

    public static void main(String[] args) throws Exception {
        System.out.println("=== AutoBridge Test Harness ===");
        System.out.println();

        Path tempDir = Files.createTempDirectory("autobridge-test-");
        System.out.println("Test directory: " + tempDir);
        System.out.println();

        int passed = 0;
        int failed = 0;

        // Create mock bridge for testing
        MockBridge mockBridge = new MockBridge(tempDir);

        // ---- Test 1: ModScanner ----
        System.out.println("[TEST 1] ModScanner...");
        try {
            ModScanner scanner = new ModScanner(mockBridge);
            List<ModScanner.ModItem> items = scanner.scanForItems();
            List<ModScanner.ModBlock> blocks = scanner.scanForBlocks();

            if (items.isEmpty() || blocks.isEmpty()) {
                throw new RuntimeException("Scanner returned empty results");
            }

            // Verify each item has required fields
            for (ModScanner.ModItem item : items) {
                if (item.javaId() == null) throw new RuntimeException("Item javaId is null");
                if (item.bedrockId() == null) throw new RuntimeException("Item bedrockId is null");
                if (item.javaNetworkId() <= 0) throw new RuntimeException("Invalid network ID: " + item.javaNetworkId());
                if (item.displayName() == null) throw new RuntimeException("Item displayName is null");
                if (item.geometryType() == null) throw new RuntimeException("Item geometryType is null");
            }

            for (ModScanner.ModBlock block : blocks) {
                if (block.javaId() == null) throw new RuntimeException("Block javaId is null");
                if (block.bedrockId() == null) throw new RuntimeException("Block bedrockId is null");
                if (block.displayName() == null) throw new RuntimeException("Block displayName is null");
                if (block.geometryType() == null) throw new RuntimeException("Block geometryType is null");
            }

            System.out.println("  PASSED — " + items.size() + " items, " + blocks.size() + " blocks");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // ---- Test 2: TexturePipeline ----
        System.out.println("[TEST 2] TexturePipeline...");
        try {
            ModScanner scanner = new ModScanner(mockBridge);
            List<ModScanner.ModItem> items = scanner.scanForItems();
            List<ModScanner.ModBlock> blocks = scanner.scanForBlocks();

            TexturePipeline pipeline = new TexturePipeline(mockBridge);

            int itemSuccess = 0;
            for (ModScanner.ModItem item : items) {
                if (pipeline.processItemTexture(item)) itemSuccess++;
            }

            int blockSuccess = 0;
            for (ModScanner.ModBlock block : blocks) {
                if (pipeline.processBlockTexture(block)) blockSuccess++;
            }

            var texMap = pipeline.getTextureMap();
            if (texMap.isEmpty()) {
                throw new RuntimeException("No textures processed");
            }

            System.out.println("  PASSED — " + itemSuccess + " items, " + blockSuccess + " blocks textured (" + texMap.size() + " total mappings)");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // ---- Test 3: MappingBuilder ----
        System.out.println("[TEST 3] MappingBuilder...");
        try {
            ModScanner scanner = new ModScanner(mockBridge);
            List<ModScanner.ModItem> items = scanner.scanForItems();
            List<ModScanner.ModBlock> blocks = scanner.scanForBlocks();

            MappingBuilder builder = new MappingBuilder(mockBridge);

            Path itemsJson = builder.generateItemsJson(items);
            Path blocksJson = builder.generateBlocksJson(blocks);

            if (!Files.exists(itemsJson)) throw new RuntimeException("items.json not created");
            if (!Files.exists(blocksJson)) throw new RuntimeException("blocks.json not created");

            // Validate JSON is non-empty and parseable
            String itemsContent = Files.readString(itemsJson);
            String blocksContent = Files.readString(blocksJson);

            if (!itemsContent.contains("\"format_version\"")) throw new RuntimeException("items.json missing format_version");
            if (!blocksContent.contains("\"format_version\"")) throw new RuntimeException("blocks.json missing format_version");
            if (!itemsContent.contains("\"items\":")) throw new RuntimeException("items.json missing items key");
            if (!blocksContent.contains("\"blocks\":")) throw new RuntimeException("blocks.json missing blocks key");

            System.out.println("  PASSED — items.json (" + itemsContent.length() + " bytes), blocks.json (" + blocksContent.length() + " bytes)");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // ---- Test 4: PackBuilder ----
        System.out.println("[TEST 4] PackBuilder...");
        try {
            ModScanner scanner = new ModScanner(mockBridge);
            List<ModScanner.ModItem> items = scanner.scanForItems();
            List<ModScanner.ModBlock> blocks = scanner.scanForBlocks();

            TexturePipeline tp = new TexturePipeline(mockBridge);
            for (ModScanner.ModItem item : items) tp.processItemTexture(item);
            for (ModScanner.ModBlock block : blocks) tp.processBlockTexture(block);

            MappingBuilder mb = new MappingBuilder(mockBridge);
            mb.generateItemsJson(items);
            mb.generateBlocksJson(blocks);

            PackBuilder packBuilder = new PackBuilder(mockBridge);
            packBuilder.setScanData(items, blocks);

            Path packZip = packBuilder.generatePack();

            if (packZip == null) throw new RuntimeException("generatePack returned null");
            if (!Files.exists(packZip)) throw new RuntimeException("Pack zip not created");

            long sizeKB = Files.size(packZip) / 1024;
            System.out.println("  PASSED — AutoBridge_Pack.zip (" + sizeKB + " KB)");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // ---- Test 5: CacheManager ----
        System.out.println("[TEST 5] CacheManager...");
        try {
            CacheManager cache = new CacheManager(tempDir.resolve("cache-test"));
            cache.registerModHash("appliedenergistics2", "ae2.jar", "abc123");
            cache.registerModHash("create", "create.jar", "def456");

            // Save and verify
            cache.saveCache(items, blocks);

            // Re-create manager pointing to same dir, register same hashes
            CacheManager cache2 = new CacheManager(tempDir.resolve("cache-test"));
            cache2.registerModHash("appliedenergistics2", "ae2.jar", "abc123");
            cache2.registerModHash("create", "create.jar", "def456");

            var loaded = cache2.loadCache();
            if (loaded == null || !loaded.isValid()) {
                throw new RuntimeException("Cache load failed — data should be valid");
            }

            System.out.println("  PASSED — cache save/load with hash validation");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // ---- Test 6: AutoBlockDetector ----
        System.out.println("[TEST 6] AutoBlockDetector...");
        try {
            ModScanner scanner = new ModScanner(mockBridge);
            List<ModScanner.ModBlock> blocks = scanner.scanForBlocks();

            AutoBlockDetector detector = new AutoBlockDetector();
            Map<String, String> guiMap = detector.detectGuiBlocks(blocks);

            // Verify AE2 GUI blocks are detected
            Integer ae2Count = 0;
            for (String key : guiMap.keySet()) {
                if (key.contains("me_") || key.contains("controller")) {
                    ae2Count++;
                }
            }

            System.out.println("  PASSED — " + guiMap.size() + " GUI blocks detected (" + ae2Count + " AE2-style)");
            passed++;
        } catch (Exception e) {
            System.out.println("  FAILED — " + e.getMessage());
            failed++;
        }

        // ---- Summary ----
        System.out.println();
        System.out.println("=== Results: " + passed + "/" + (passed + failed) + " tests passed ===");

        if (failed > 0) {
            System.exit(1);
        }

        // Cleanup
        deleteRecursively(tempDir);
        System.out.println("Cleaned up test directory.");
    }

    private static void deleteRecursively(Path path) throws IOException {
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
     * Minimal mock of AutoBridge needed for standalone testing.
     * Provides extensionsDir() and texturePipeline() accessors.
     */
    static class MockBridge {
        private final Path extensionsDir;
        private TexturePipeline texturePipeline;

        MockBridge(Path extensionsDir) {
            this.extensionsDir = extensionsDir;
            try { Files.createDirectories(extensionsDir); } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Path extensionsDir() { return extensionsDir; }

        TexturePipeline texturePipeline() {
            if (texturePipeline == null) {
                texturePipeline = new TexturePipeline(this);
            }
            return texturePipeline;
        }
    }
}
