package autobridge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AutoBlockDetector — Automatically detects which blocks have custom GUIs
 * and builds a mapping of block → GUI type.
 *
 * Scans block properties, registry names, and known patterns to identify
 * blocks that open custom interfaces when right-clicked. This data is used
 * by GuiTranslator to know which form to show Bedrock players.
 *
 * Detection strategies:
 * 1. Name-based pattern matching (e.g., "terminal", "controller", "encoder")
 * 2. Block property analysis (blocks with complex state definitions often have GUIs)
 * 3. Known mod GUI signatures (AE2, Create, etc.)
 */
public class AutoBlockDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoBlockDetector");

    // GUI type keywords that indicate a block has an interactive interface
    private static final String[] GUI_KEYWORDS = {
        "terminal", "interface", "controller", "cpu", "encoder", "fabricator",
        "machine", "press", "assembler", "channeler", "network", "storage",
        "cell", "interface", "work", "bench", "anvil", "loom", "furnace",
        "crafting", "grid", "portable", "secure", "wireless"
    };

    // AE2-specific GUI block patterns
    private static final Map<String, String> AE2_GUI_PATTERNS = Map.of(
        "me_controller", "me_terminal",
        "me_terminal", "me_terminal",
        "me_pattern_encoding", "pattern_encoder",
        "me_pattern_accelerator", "me_terminal",
        "crafting_cpu", "crafting_cpu",
        "me_export_bus", "generic",
        "me_import_bus", "generic",
        "me_fluid_terminal", "me_terminal"
    );

    // Detected GUI mappings: bedrockName → guiType
    private final Map<String, String> guiMappings = new HashMap<>();

    /**
     * Scan all discovered blocks for GUI indicators.
     * Returns a map of bedrock block name → GUI type string.
     */
    public Map<String, String> detectGuiBlocks(List<ModScanner.ModBlock> blocks) {
        LOGGER.info("AutoBridge/Detector: Scanning {} blocks for GUI indicators...", blocks.size());

        for (ModScanner.ModBlock block : blocks) {
            String guiType = detectGuiType(block);
            if (guiType != null && !guiType.equals("none")) {
                String bedrockName = block.bedrockId().getPath();
                guiMappings.put(bedrockName, guiType);
                LOGGER.debug("AutoBridge/Detector: Block {} → GUI type: {}", bedrockName, guiType);
            }
        }

        LOGGER.info("AutoBridge/Detector: Found {} blocks with GUIs out of {}",
            guiMappings.size(), blocks.size());

        return Map.copyOf(guiMappings);
    }

    /**
     * Detect whether a block opens a custom GUI when interacted with.
     * Uses multiple detection strategies in priority order.
     */
    private String detectGuiType(ModScanner.ModBlock block) {
        String blockPath = block.javaId().getPath().toLowerCase();
        String namespace = block.javaId().getNamespace();

        // Strategy 1: Check against known mod GUI patterns (highest accuracy)
        String knownPattern = checkKnownPatterns(namespace, blockPath);
        if (knownPattern != null) return knownPattern;

        // Strategy 2: Keyword matching on block name
        String keywordMatch = checkKeywords(blockPath);
        if (keywordMatch != null) return keywordMatch;

        // Strategy 3: Block property complexity heuristic
        // Blocks with many states often have GUIs (e.g., machines with tiers)
        if (hasComplexStates(block)) {
            return "complex_gui";
        }

        return null;
    }

    /**
     * Check against known mod GUI signatures.
     * These are high-confidence detections based on exact block names.
     */
    private String checkKnownPatterns(String namespace, String blockPath) {
        // AE2 patterns
        if ("appliedenergistics2".equals(namespace)) {
            return AE2_GUI_PATTERNS.getOrDefault(blockPath, null);
        }

        // Expandable patterns for other mods would go here
        // Each mod gets its own pattern map as we discover them

        return null;
    }

    /**
     * Check block name against GUI indicator keywords.
     * Lower confidence than known patterns but catches unknown mods.
     */
    private String checkKeywords(String blockPath) {
        for (String keyword : GUI_KEYWORDS) {
            if (blockPath.contains(keyword)) {
                // Classify the GUI type based on the matched keyword
                if (keyword.equals("terminal") || keyword.equals("interface")) {
                    return "inventory_gui";
                } else if (keyword.equals("controller") || keyword.equals("cpu")) {
                    return "control_gui";
                } else if (keyword.equals("encoder") || keyword.equals("fabricator")) {
                    return "crafting_gui";
                } else if (keyword.equals("machine") || keyword.equals("press") || keyword.equals("assembler")) {
                    return "machine_gui";
                } else {
                    return "generic_gui";
                }
            }
        }
        return null;
    }

    /**
     * Heuristic: blocks with many properties likely have GUIs.
     * This is a fallback detection for blocks we don't recognize.
     */
    private boolean hasComplexStates(ModScanner.ModBlock block) {
        try {
            var blockRegistry = BuiltInRegistries.BLOCK;
            Block minecraftBlock = blockRegistry.get(block.javaId());
            if (minecraftBlock == null) return false;

            int propCount = minecraftBlock.getStateDefinition().getProperties().size();
            int stateCount = minecraftBlock.getStateDefinition().getPossibleStates().size();

            // Blocks with >8 states or >4 properties are likely GUI-containing
            return stateCount > 8 || propCount > 4;

        } catch (Exception e) {
            // If we can't access the registry, fall back to light emission
            // (light-emitting blocks are less likely to be GUI blocks)
            return block.lightEmission() == 0 && block.geometryType() == ModScanner.GeometryType.COMPLEX;
        }
    }

    /**
     * Get the GUI type for a specific bedrock block name.
     * Returns null if the block has no detected GUI.
     */
    public String getGuiType(String bedrockBlockName) {
        return guiMappings.get(bedrockBlockName);
    }

    /**
     * Returns all detected GUI mappings.
     */
    public Map<String, String> getAllMappings() {
        return Map.copyOf(guiMappings);
    }

    /**
     * Returns the count of detected GUI blocks.
     */
    public int getGuiCount() {
        return guiMappings.size();
    }
}
