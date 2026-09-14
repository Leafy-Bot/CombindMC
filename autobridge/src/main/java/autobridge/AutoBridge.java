package autobridge;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import org.geysermc.geyser.api.item.custom.v2.NonVanillaCustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserBlockPlacer;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserItemDataComponents;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaConsumable;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaItemDataComponents;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaSwingAnimation;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.geysermc.geyser.api.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * AutoBridge — Geyser Extension for automatic mod translation.
 *
 * Uses verified Geyser API 2.11.2-SNAPSHOT signatures (confirmed via javap).
 * The full pipeline runs automatically at startup with zero manual configuration.
 */
public class AutoBridge implements Extension {

    private ModScanner modScanner;
    private TexturePipeline texturePipeline;
    private MappingBuilder mappingBuilder;
    private PackBuilder packBuilder;
    private GuiTranslator guiTranslator;
    private CacheManager cacheManager;
    private AutoBlockDetector blockDetector;

    private List<ModScanner.ModItem> cachedItems;
    private List<ModScanner.ModBlock> cachedBlocks;
    
    private long startTime;
    private AutoBridgeConfig config;

    @Override
    public ExtensionLogger logger() {
        return Extension.super.logger();
    }

    // ==================== Lifecycle Events ====================

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        startTime = System.currentTimeMillis();
        logger().info("AutoBridge initializing — starting full auto-pipeline...");

        Path dataDir = dataFolder();
        
        // Load configuration
        this.config = new AutoBridgeConfig(dataDir);
        if (config.isVerboseLogging()) {
            logger().info("Configuration loaded from " + dataDir.resolve("autobridge.properties"));
        }
        
        Path mappingsDir = dataDir.resolve("custom_mappings");
        Path texturesDir = dataDir.resolve("generated_textures");
        Path cacheDir = dataDir.resolve("cache");

        try {
            Files.createDirectories(mappingsDir);
            Files.createDirectories(texturesDir);
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            logger().error("Failed to create directories: " + e.getMessage());
        }

        // Initialize pipeline modules
        this.texturePipeline = new TexturePipeline(texturesDir);
        this.mappingBuilder = new MappingBuilder(mappingsDir);
        this.packBuilder = new PackBuilder(dataDir);
        this.guiTranslator = new GuiTranslator();
        this.cacheManager = new CacheManager(cacheDir);
        this.blockDetector = new AutoBlockDetector();

        try {
            // Phase 0: Check cache
            if (config.isEnableCache()) {
                long cacheStart = System.currentTimeMillis();
                CacheManager.CachedScanData cached = cacheManager.loadCache();
                if (cached != null && cached.isValid()) {
                    logger().info("Loaded from cache in " + (System.currentTimeMillis() - cacheStart) + "ms — but still performing full scan for accuracy");
                }
            }

            // Phase 1: Scan mods directory
            long scanStart = System.currentTimeMillis();
            Path modsDir = findModsDir();
            this.modScanner = new ModScanner(modsDir);
            ModScanner.ScanResult result = modScanner.scan();
            cachedItems = result.items();
            cachedBlocks = result.blocks();
            long scanTime = System.currentTimeMillis() - scanStart;

            logger().info("Discovered " + cachedItems.size() + " items, " + cachedBlocks.size()
                + " blocks from " + result.mods().size() + " mods (scanned in " + scanTime + "ms)");

            // Phase 1.5: Detect GUI blocks
            if (config.isAutoDetectGuiBlocks()) {
                long guiStart = System.currentTimeMillis();
                blockDetector.detectGuiBlocks(cachedBlocks);
                logger().info("Detected " + blockDetector.getGuiCount() + " GUI blocks in " + (System.currentTimeMillis() - guiStart) + "ms");
            }

            // Phase 2: Extract textures
            long textureStart = System.currentTimeMillis();
            int itemsOk = 0, blocksOk = 0;
            for (ModScanner.ModItem item : cachedItems) {
                if (texturePipeline.processItemTexture(item, modsDir)) itemsOk++;
            }
            for (ModScanner.ModBlock block : cachedBlocks) {
                if (texturePipeline.processBlockTexture(block, modsDir)) blocksOk++;
            }
            long textureTime = System.currentTimeMillis() - textureStart;
            logger().info("Textures: " + itemsOk + " items, " + blocksOk + " blocks processed in " + textureTime + "ms");

            // Phase 3: Generate mappings
            long mappingStart = System.currentTimeMillis();
            mappingBuilder.generateItemsJson(cachedItems);
            mappingBuilder.generateBlocksJson(cachedBlocks);
            long mappingTime = System.currentTimeMillis() - mappingStart;
            logger().info("Generated " + mappingBuilder.getMappingCount() + " mappings in " + mappingTime + "ms");

            // Phase 4: Build resource pack
            long packStart = System.currentTimeMillis();
            Path pack = packBuilder.generatePack(texturePipeline, cachedItems, cachedBlocks);
            long packTime = System.currentTimeMillis() - packStart;
            if (pack != null) {
                logger().info("Resource pack generated in " + packTime + "ms: " + pack);
            }

            // Phase 5: Save cache
            if (config.isEnableCache()) {
                cacheManager.saveCache(cachedItems, cachedBlocks);
            }

            long totalTime = System.currentTimeMillis() - startTime;
            logger().info("AutoBridge auto-pipeline complete (total time: " + totalTime + "ms)");

        } catch (Exception e) {
            logger().error("Auto-pipeline failed: " + e.getMessage());
            if (config.isVerboseLogging()) {
                e.printStackTrace();
            }
        }
    }

    @Subscribe
    public void onDefineCustomItems(GeyserDefineCustomItemsEvent event) {
        if (cachedItems == null || cachedItems.isEmpty()) return;

        logger().info("Auto-registering " + cachedItems.size() + " custom items...");
        int registered = 0, failed = 0;

        for (ModScanner.ModItem item : cachedItems) {
            try {
                Identifier javaId = Identifier.of(item.namespace(), item.path());
                Identifier bedrockId = Identifier.of(
                    item.bedrockId().substring(0, item.bedrockId().indexOf(':')),
                    item.bedrockId().substring(item.bedrockId().indexOf(':') + 1)
                );

                // 3-arg builder: javaIdentifier, bedrockIdentifier, networkId
                var builder = NonVanillaCustomItemDefinition.builder(javaId, bedrockId, item.javaNetworkId());
                builder.displayName(item.displayName());

                // Auto-apply components
                if (item.maxStackSize() > 1) {
                    builder.component(JavaItemDataComponents.MAX_STACK_SIZE, item.maxStackSize());
                }
                if (item.isConsumable()) {
                    builder.component(JavaItemDataComponents.CONSUMABLE,
                        JavaConsumable.builder().consumeSeconds(1.0f).animation(JavaConsumable.Animation.EAT).build());
                }
                switch (item.geometryType()) {
                    case EQUIPPABLE -> {
                        builder.component(GeyserItemDataComponents.ATTACK_DAMAGE, 1);
                        builder.component(JavaItemDataComponents.SWING_ANIMATION,
                            JavaSwingAnimation.builder().duration(12).build());
                    }
                    case MODEL_3D -> {
                        Identifier blockId = Identifier.of(item.bedrockId().replace(':', '_'));
                        builder.component(GeyserItemDataComponents.BLOCK_PLACER,
                            GeyserBlockPlacer.builder().block(blockId).useBlockIcon(false).build());
                    }
                    default -> {}
                }

                event.register(builder.build());
                registered++;
            } catch (Exception e) {
                failed++;
                logger().error("Failed to register item " + item.javaId() + ": " + e.getMessage());
            }
        }

        logger().info("Items registered: " + registered + " success, " + failed + " failed");
    }

    @Subscribe
    public void onDefineCustomBlocks(GeyserDefineCustomBlocksEvent event) {
        if (cachedBlocks == null || cachedBlocks.isEmpty()) return;

        logger().info("Auto-registering " + cachedBlocks.size() + " custom blocks...");
        int registered = 0, failed = 0;

        for (ModScanner.ModBlock block : cachedBlocks) {
            try {
                String bedrockName = block.bedrockId().replace(':', '_');

                GeometryComponent geometry = GeometryComponent.builder()
                    .identifier("minecraft:geometry.full_block")
                    .build();

                MaterialInstance material = MaterialInstance.builder()
                    .texture(bedrockName)
                    .renderMethod("alphatest")
                    .build();

                CustomBlockComponents components = CustomBlockComponents.builder()
                    .geometry(geometry)
                    .materialInstance("*", material)
                    .destructibleByMining(1.0f)
                    .friction(block.friction())
                    .displayName(block.displayName())
                    .build();

                // Use NonVanillaCustomBlockData for modded blocks
                NonVanillaCustomBlockData blockData = NonVanillaCustomBlockData.builder()
                    .namespace(block.namespace())
                    .name(bedrockName)
                    .includedInCreativeInventory(true)
                    .creativeCategory(CreativeCategory.CONSTRUCTION)
                    .creativeGroup(block.namespace())
                    .components(components)
                    .build();

                event.register(blockData);
                event.registerItemOverride(block.javaId(), blockData);
                registered++;
            } catch (Exception e) {
                failed++;
                logger().error("Failed to register block " + block.javaId() + ": " + e.getMessage());
            }
        }

        logger().info("Blocks registered: " + registered + " success, " + failed + " failed");
    }

    @Subscribe
    public void onDefineResourcePacks(GeyserDefineResourcePacksEvent event) {
        if (packBuilder == null) return;
        Path packPath = packBuilder.getLastGeneratedPack();
        if (packPath != null && Files.exists(packPath)) {
            try {
                ResourcePack pack = ResourcePack.create(PackCodec.path(packPath));
                event.register(pack);
                logger().info("Sent auto-generated resource pack to Bedrock clients");
            } catch (Exception e) {
                logger().error("Failed to load resource pack: " + e.getMessage());
            }
        }
    }

    @Subscribe
    public void onShutdown(GeyserShutdownEvent event) {
        logger().info("AutoBridge shutting down. Mappings: " + (mappingBuilder != null ? mappingBuilder.getMappingCount() : 0)
            + ", Packs: " + (packBuilder != null ? packBuilder.getPackCount() : 0)
            + ", GUIs: " + (blockDetector != null ? blockDetector.getGuiCount() : 0));
    }

    // ==================== Helpers ====================

    /**
     * Find the mods directory. Checks config override first, then common locations.
     */
    private Path findModsDir() {
        // Check config override first
        String override = config.getModsDirectoryOverride();
        if (override != null && !override.isEmpty()) {
            Path overridePath = Path.of(override);
            if (Files.exists(overridePath) && Files.isDirectory(overridePath)) {
                logger().info("Using mods directory from config: " + overridePath);
                return overridePath;
            }
        }
        
        // Check common locations
        Path[] candidates = {
            Path.of("mods"),
            Path.of(System.getProperty("user.dir"), "mods"),
            dataFolder().resolve("mods")
        };
        for (Path p : candidates) {
            if (Files.exists(p) && Files.isDirectory(p)) return p;
        }
        // Default to dataFolder/mods
        Path defaultDir = dataFolder().resolve("mods");
        try { Files.createDirectories(defaultDir); } catch (IOException ignored) {}
        return defaultDir;
    }
}
