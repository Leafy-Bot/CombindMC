package autobridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AutoBlockDetector — Detects which blocks have custom GUIs by name patterns.
 */
public class AutoBlockDetector {

    private static final String[] GUI_KEYWORDS = {
        "terminal", "interface", "controller", "cpu", "encoder", "fabricator",
        "machine", "press", "assembler", "channeler", "network", "storage",
        "cell", "work", "bench", "crafting", "grid", "portable", "secure", "wireless"
    };

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

    private final Map<String, String> guiMappings = new HashMap<>();

    public Map<String, String> detectGuiBlocks(List<ModScanner.ModBlock> blocks) {
        System.out.println("[AutoBlockDetector] Scanning " + blocks.size() + " blocks for GUI indicators...");

        for (ModScanner.ModBlock block : blocks) {
            String guiType = detectGuiType(block);
            if (guiType != null && !guiType.equals("none")) {
                guiMappings.put(block.bedrockId(), guiType);
            }
        }

        System.out.println("[AutoBlockDetector] Found " + guiMappings.size() + " GUI blocks out of " + blocks.size());
        return Map.copyOf(guiMappings);
    }

    private String detectGuiType(ModScanner.ModBlock block) {
        String blockPath = block.path().toLowerCase();
        String namespace = block.namespace();

        // Strategy 1: Known patterns
        if ("appliedenergistics2".equals(namespace) || "ae2".equals(namespace)) {
            String known = AE2_GUI_PATTERNS.get(blockPath);
            if (known != null) return known;
        }

        // Strategy 2: Keyword matching
        for (String keyword : GUI_KEYWORDS) {
            if (blockPath.contains(keyword)) {
                return switch (keyword) {
                    case "terminal", "interface" -> "inventory_gui";
                    case "controller", "cpu" -> "control_gui";
                    case "encoder", "fabricator" -> "crafting_gui";
                    case "machine", "press", "assembler" -> "machine_gui";
                    default -> "generic_gui";
                };
            }
        }

        // Strategy 3: State complexity
        if (block.stateCount() > 8) {
            return "complex_gui";
        }

        return null;
    }

    public String getGuiType(String bedrockBlockName) { return guiMappings.get(bedrockBlockName); }
    public Map<String, String> getAllMappings() { return Map.copyOf(guiMappings); }
    public int getGuiCount() { return guiMappings.size(); }
}
