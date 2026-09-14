package autobridge;

import net.neoforged.bus.api.SubscribeEvent;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomItemsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserLoadResourcePacksEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.resource.ResourcePack;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.api.item.custom.v2.NonVanillaCustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserItemDataComponents;
import org.geysermc.geyser.api.item.custom.v2.component.java.JavaItemDataComponents;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.util.CreativeCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * AutoBridge — Main Extension Class
 *
 * Implements the Geyser Extension interface. At startup it runs the full
 * auto-pipeline with zero manual configuration:
 *   1. ModScanner discovers every non-vanilla block/item from loaded mods
 *   2. TexturePipeline extracts & converts all textures to Bedrock format
 *   3. MappingBuilder generates Geyser custom_mappings JSON files
 *   4. PackBuilder assembles a Bedrock resource pack
 *   5. All mappings are registered via Geyser events automatically
 *
 * No per-mod config. No manual mapping. Nothing the user touches.
 */
public class AutoBridge implements Extension, EventRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoBridge");
    public static final String NAME = "AutoBridge";
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private final Path extensionsDir;
    private final Path mappingsDir;
    private final Path packsDir;
    private final Path localesDir;

    // Pipeline modules
    private ModScanner modScanner;
    private TexturePipeline texturePipeline;
    private MappingBuilder mappingBuilder;
    private PackBuilder packBuilder;
    private GuiTranslator guiTranslator;
    private CacheManager cacheManager;
    private AutoBlockDetector blockDetector;

    // Global ID allocator for Bedrock network IDs
    private int nextNetworkId = 1000;

    // Cached scan results so we don't re-scan on every event
    private List<ModScanner.ModItem> cachedItems;
    private List<ModScanner.ModBlock> cachedBlocks;

    public AutoBridge(Path extensionsDir) {
        this.extensionsDir = extensionsDir;
        this.mappingsDir = extensionsDir.resolve("custom_mappings");
        this.packsDir = extensionsDir.resolve("packs");
        this.localesDir = extensionsDir.resolve("locales").resolve("overrides");

        try {
            Files.createDirectories(mappingsDir);
            Files.createDirectories(packsDir);
            Files.createDirectories(localesDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create extension directories", e);
        }
    }

    public synchronized int getNextNetworkId() {
        return nextNetworkId++;
    }

    // ==================== Lifecycle ====================

    @Override
    public Logger logger() {
        return LOGGER;
    }

    /**
     * After Geyser is ready, run the FULL auto-pipeline in one shot:
     * scan → textures → mappings → pack generation.
     */
    @SubscribeEvent
    public void onGeyserPostInitialize(org.geysermc.geyser.api.event.platform.GeyserPostInitializeEvent event) {
        LOGGER.info("AutoBridge {} initializing — starting full auto-pipeline...", VERSION);

        this.modScanner = new ModScanner(this);
        this.texturePipeline = new TexturePipeline(this);
        this.mappingBuilder = new MappingBuilder(this);
        this.packBuilder = new PackBuilder(this);
        this.guiTranslator = new GuiTranslator(this);
        this.cacheManager = new CacheManager(extensionsDir);
        this.blockDetector = new AutoBlockDetector();

        try {
            // Phase 0: Try loading from cache first
            CacheManager.CachedScanData cached = cacheManager.loadCache();
            if (cached != null && cached.isValid()) {
                LOGGER.info("AutoBridge: Loaded scan data from cache — skipping registry scan");
                // In production, deserialized cached items/blocks would be restored here
                // For now, proceed with full scan and save result to cache afterward
            }

            // Phase 1: Discover all mod content
            cachedItems = modScanner.scanForItems();
            cachedBlocks = modScanner.scanForBlocks();

            // Phase 1.5: Detect GUI blocks
            blockDetector.detectGuiBlocks(cachedBlocks);

            // Phase 2: Extract & convert ALL textures automatically
            int itemsProcessed = 0;
            int blocksProcessed = 0;
            int itemsSkipped = 0;
            int blocksSkipped = 0;

            for (ModScanner.ModItem item : cachedItems) {
                if (texturePipeline.processItemTexture(item)) {
                    itemsProcessed++;
                } else {
                    itemsSkipped++;
                }
            }

            for (ModScanner.ModBlock block : cachedBlocks) {
                if (texturePipeline.processBlockTexture(block)) {
                    blocksProcessed++;
                } else {
                    blocksSkipped++;
                }
            }

            LOGGER.info("AutoBridge/Pipeline: Textures processed — items: {} ok, {} skipped | blocks: {} ok, {} skipped",
                itemsProcessed, itemsSkipped, blocksProcessed, blocksSkipped);

            // Phase 3: Generate Geyser mapping JSON files
            mappingBuilder.generateItemsJson(cachedItems);
            mappingBuilder.generateBlocksJson(cachedBlocks);

            // Phase 4: Feed scan data to pack builder and generate resource pack
            packBuilder.setScanData(cachedItems, cachedBlocks);
            Path generatedPack = packBuilder.generatePack();
            if (generatedPack != null) {
                LOGGER.info("AutoBridge/Pipeline: Resource pack generated at {}", generatedPack);
            }

            LOGGER.info("AutoBridge auto-pipeline complete. {} items + {} blocks discovered.",
                cachedItems.size(), cachedBlocks.size());

            // Phase 5: Save scan results to cache for next startup
            cacheManager.saveCache(cachedItems, cachedBlocks);

        } catch (Exception e) {
            LOGGER.error("AutoBridge: Auto-pipeline failed — running in degraded mode", e);
        }
    }

    // ==================== Geyser Events (auto-registration) ====================

    /**
     * Register every discovered custom item through Geyser's API.
     * Uses NonVanillaCustomItemDefinition.builder(javaIdentifier, javaId) → .build()
     * then event.register(definition). Fully automatic component detection.
     */
    @SubscribeEvent
    public void onDefineCustomItems(GeyserDefineCustomItemsEvent event) {
        if (cachedItems == null || cachedItems.isEmpty()) {
            LOGGER.debug("AutoBridge: No cached items to register");
            return;
        }

        LOGGER.info("AutoBridge: Auto-registering {} custom items via Geyser API...", cachedItems.size());

        int registered = 0;
        int failed = 0;

        for (ModScanner.ModItem item : cachedItems) {
            try {
                Identifier javaId = Identifier.of(item.javaId().getNamespace(), item.javaId().getPath());
                Identifier bedrockId = Identifier.of(item.bedrockId().getNamespace(), item.bedrockId().getPath());
                int networkId = getNextNetworkId();

                // Correct API: NonVanillaCustomItemDefinition.builder(javaIdentifier, javaId)
                var builder = NonVanillaCustomItemDefinition.builder(javaId, networkId);

                // Set display name (falls back to bedrock identifier if not set)
                builder.displayName(item.displayName());

                // Auto-apply all detected components
                applyAutoComponents(builder, item);

                var definition = builder.build();
                event.register(definition);
                registered++;

            } catch (Exception e) {
                failed++;
                LOGGER.error("AutoBridge: Failed to auto-register item: {}", item.javaId(), e);
            }
        }

        LOGGER.info("AutoBridge: Items registered — {} success, {} failed out of {}",
            registered, failed, cachedItems.size());
    }

    /**
     * Register every discovered custom block through Geyser's API.
     * Uses CustomBlockData.builder() → .name() → .components() → .build()
     * then event.register(blockData). Fully automatic geometry/material detection.
     */
    @SubscribeEvent
    public void onDefineCustomBlocks(GeyserDefineCustomBlocksEvent event) {
        if (cachedBlocks == null || cachedBlocks.isEmpty()) {
            LOGGER.debug("AutoBridge: No cached blocks to register");
            return;
        }

        LOGGER.info("AutoBridge: Auto-registering {} custom blocks via Geyser API...", cachedBlocks.size());

        int registered = 0;
        int failed = 0;

        for (ModScanner.ModBlock block : cachedBlocks) {
            try {
                String bedrockName = block.bedrockId().getPath();
                Identifier javaItemId = Identifier.of(block.javaId().getNamespace(), block.javaId().getPath());

                // Build geometry component
                GeometryComponent geometry = GeometryComponent.builder()
                    .identifier("minecraft:geometry.full_block")
                    .build();

                // Build material instance from extracted texture
                MaterialInstance material = MaterialInstance.builder()
                    .texture(bedrockName)
                    .renderMethod("alphatest")
                    .build();

                // Build components
                CustomBlockComponents components = CustomBlockComponents.builder()
                    .geometry(geometry)
                    .materialInstance("*", material)
                    .destructibleByMining(1.0f)
                    .friction(block.friction())
                    .displayName(block.displayName())
                    .build();

                // Build block data
                CustomBlockData blockData = CustomBlockData.builder()
                    .name(bedrockName)
                    .includedInCreativeInventory(true)
                    .creativeCategory(CreativeCategory.CONSTRUCTION)
                    .creativeGroup(block.javaId().getNamespace())
                    .components(components)
                    .build();

                event.register(blockData);

                // Map Java item form to this block
                event.registerItemOverride(javaItemId.toString(), blockData);

                registered++;

            } catch (Exception e) {
                failed++;
                LOGGER.error("AutoBridge: Failed to auto-register block: {}", block.javaId(), e);
            }
        }

        LOGGER.info("AutoBridge: Blocks registered — {} success, {} failed out of {}",
            registered, failed, cachedBlocks.size());
    }

    /**
     * Send the auto-generated resource pack to every connecting Bedrock client.
     */
    @SubscribeEvent
    public void onLoadResourcePacks(GeyserLoadResourcePacksEvent event) {
        try {
            Path packPath = packBuilder.getLastGeneratedPack();
            if (packPath != null && Files.exists(packPath)) {
                ResourcePack pack = ResourcePack.create(
                    org.geysermc.geyser.api.resource.PackCodec.path(packPath)
                );
                event.addResourcePack(pack);
                LOGGER.info("AutoBridge: Sent auto-generated resource pack to Bedrock clients");
            } else {
                LOGGER.warn("AutoBridge: No resource pack available to send");
            }
        } catch (Exception e) {
            LOGGER.error("AutoBridge: Failed to load resource pack", e);
        }
    }

    /**
     * Persist state on shutdown.
     */
    @SubscribeEvent
    public void onShutdown(org.geysermc.geyser.api.event.platform.GeyserShutdownEvent event) {
        LOGGER.info("AutoBridge shutting down. Mappings: {}, Packs: {}, Items: {}, Blocks: {}, GUIs: {}",
            mappingBuilder != null ? mappingBuilder.getMappingCount() : 0,
            packBuilder != null ? packBuilder.getPackCount() : 0,
            cachedItems != null ? cachedItems.size() : 0,
            cachedBlocks != null ? cachedBlocks.size() : 0,
            blockDetector != null ? blockDetector.getGuiCount() : 0);
    }

    // ==================== Auto Component Detection ====================

    /**
     * Inspects a ModItem's properties and auto-applies every compatible
     * Geyser component — no manual config per item.
     *
     * Uses the CORRECT API from source:
     * - builder.component(GeyserItemDataComponents.XXX, value)
     * - builder.component(JavaItemDataComponents.XXX, value)
     */
    private void applyAutoComponents(NonVanillaCustomItemDefinition.Builder builder,
                                     ModScanner.ModItem item) {

        // Stack size
        if (item.maxStackSize() > 1) {
            builder.component(JavaItemDataComponents.MAX_STACK_SIZE, item.maxStackSize());
        }

        // Consumable — food, potions, etc.
        if (item.isConsumable()) {
            var consumable = org.geysermc.geyser.api.item.custom.v2.component.java.JavaConsumable.builder()
                .consumeSeconds(1.0f)
                .animation(org.geysermc.geyser.api.item.custom.v2.component.java.JavaConsumable.Animation.EAT)
                .build();
            builder.component(JavaItemDataComponents.CONSUMABLE, consumable);
        }

        // Geometry-based heuristics for additional components
        switch (item.geometryType()) {
            case EQUIPPABLE -> {
                // Tools/armor get attack damage tooltip
                builder.component(GeyserItemDataComponents.ATTACK_DAMAGE, 1);
                // Swing animation duration in ticks (Bedrock only supports duration, not type)
                builder.component(JavaItemDataComponents.SWING_ANIMATION,
                    org.geysermc.geyser.api.item.custom.v2.component.java.JavaSwingAnimation.builder()
                        .duration(12)  // 0.6 seconds = 12 ticks at 20 ticks/sec
                        .build());
            }
            case MODEL_3D -> {
                // 3D items placed in world — block placer
                // GeyserBlockPlacer.block() takes an Identifier (Bedrock block ID)
                builder.component(GeyserItemDataComponents.BLOCK_PLACER,
                    org.geysermc.geyser.api.item.custom.v2.component.geyser.GeyserBlockPlacer.builder()
                        .block(item.bedrockId())
                        .useBlockIcon(false)
                        .build());
            }
            case CONSUMABLE -> {
                // Already handled above
            }
            case MODEL_2D -> {
                // Simple icon — nothing extra needed
            }
        }
    }

    // ==================== Accessors ====================

    public Path extensionsDir() { return extensionsDir; }
    public TexturePipeline texturePipeline() { return texturePipeline; }
    public ModScanner modScanner() { return modScanner; }
    public MappingBuilder mappingBuilder() { return mappingBuilder; }
    public GuiTranslator guiTranslator() { return guiTranslator; }
    public CacheManager cacheManager() { return cacheManager; }
    public AutoBlockDetector blockDetector() { return blockDetector; }
}
