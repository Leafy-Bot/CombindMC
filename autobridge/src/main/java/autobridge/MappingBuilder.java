package autobridge;

import org.geysermc.geyser.api.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * MappingBuilder — Fully automatic generation of Geyser custom_mappings JSON.
 *
 * Takes the complete ModItem/ModBlock lists (with all metadata including textures)
 * and produces valid Geyser v2 format JSON files. Zero manual config per item/block.
 */
public class MappingBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger("MappingBuilder");

    private final AutoBridge bridge;
    private int mappingCount = 0;

    public MappingBuilder(AutoBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * Generate items.json for ALL discovered mod items.
     * Uses NonVanillaCustomItemDefinition-compatible JSON format with network IDs.
     */
    public Path generateItemsJson(List<ModScanner.ModItem> items) throws IOException {
        Path outputFile = bridge.extensionsDir().resolve("custom_mappings").resolve("items.json");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"format_version\": 2,\n");
        json.append("  \"items\": {\n");

        int total = items.size();
        int idx = 0;

        for (ModScanner.ModItem item : items) {
            idx++;
            String bedrockName = item.bedrockId().getPath();

            json.append("    ").append(jsonKey(item.javaId().toString())).append(": [\n");
            json.append("      {\n");

            // Bedrock identifier
            json.append("        \"bedrock_identifier\": ").append(jsonKey(item.bedrockId().toString())).append(",\n");

            // Definition with model reference
            json.append("        \"definition\": {\n");
            json.append("          \"model\": \"").append(bedrockName).append("\"\n");
            json.append("        },\n");

            // Display name from registry
            json.append("        \"display_name\": \"").append(escapeJson(item.displayName())).append("\",\n");

            // Bedrock options
            json.append("        \"bedrock_options\": {\n");
            json.append("          \"icon\": \"").append(bedrockName).append("\",\n");

            String creativeCat = switch (item.geometryType()) {
                case EQUIPPABLE -> "equipment";
                case CONSUMABLE -> "food";
                default -> "buildingBlocks";
            };
            json.append("          \"creative_category\": \"").append(creativeCat).append("\",\n");
            json.append("          \"creative_group\": \"").append(escapeJson(item.javaId().getNamespace())).append("\"\n");
            json.append("        },\n");

            // Components — auto-detected from scanned properties
            json.append("        \"components\": {\n");

            boolean hasComponents = false;

            if (item.maxStackSize() > 1) {
                json.append("          \"minecraft:max_stack_size\": ").append(item.maxStackSize());
                hasComponents = true;
            }

            if (item.isConsumable()) {
                if (hasComponents) json.append(",\n");
                json.append("          \"minecraft:consumable\": {\n");
                json.append("            \"consume_seconds\": 1.0,\n");
                json.append("            \"consuming_item_notification\": true\n");
                json.append("          }");
                hasComponents = true;
            }

            if (!hasComponents) {
                json.append("          \"minecraft:usable_by\": [\"player\"]");
            }

            json.append("\n        }\n");
            json.append("      }\n");
            json.append("    ]");

            if (idx < total) json.append(",");
            json.append("\n");

            mappingCount++;

            if (idx % 50 == 0) {
                LOGGER.info("AutoBridge/Mappings: Generated {} of {} item mappings...", idx, total);
            }
        }

        json.append("  }\n");
        json.append("}");

        Files.writeString(outputFile, json.toString());
        LOGGER.info("AutoBridge/Mappings: Wrote items.json with {} entries → {}", total, outputFile);

        return outputFile;
    }

    /**
     * Generate blocks.json for ALL discovered mod blocks.
     */
    public Path generateBlocksJson(List<ModScanner.ModBlock> blocks) throws IOException {
        Path outputFile = bridge.extensionsDir().resolve("custom_mappings").resolve("blocks.json");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"format_version\": 1,\n");
        json.append("  \"blocks\": {\n");

        int total = blocks.size();
        int idx = 0;

        for (ModScanner.ModBlock block : blocks) {
            idx++;
            String bedrockName = block.bedrockId().getPath();

            json.append("    ").append(jsonKey(block.javaId().toString())).append(": {\n");
            json.append("      \"name\": \"").append(bedrockName).append("\",\n");
            json.append("      \"geometry\": \"minecraft:geometry.full_block\",\n");

            json.append("      \"material_instances\": {\n");
            json.append("        \"*\": {\n");
            json.append("          \"type\": \"texture\",\n");
            json.append("          \"texture\": \"").append(bedrockName).append("\"\n");
            json.append("        }\n");
            json.append("      },\n");

            // State permutations — blocks with multiple states get state_overrides
            if (block.stateCount() > 1) {
                json.append("      \"state_overrides\": {\n");
                int stateIdx = 0;
                int maxStatesToShow = Math.min(block.stateCount(), 16); // Bedrock limit
                for (int i = 0; i < maxStatesToShow; i++) {
                    String stateName = bedrockName + "_state_" + i;
                    json.append("        ").append(stateIdx).append(": {\n");
                    json.append("          \"name\": \"").append(stateName).append("\",\n");
                    json.append("          \"geometry\": \"minecraft:geometry.full_block\",\n");
                    json.append("          \"material_instances\": {\n");
                    json.append("            \"*\": {\n");
                    json.append("              \"type\": \"texture\",\n");
                    json.append("              \"texture\": \"").append(bedrockName).append("_state_").append(i).append("\"\n");
                    json.append("            }\n");
                    json.append("          }\n");
                    json.append("        }");
                    stateIdx++;
                    if (stateIdx < maxStatesToShow) json.append(",");
                    json.append("\n");
                }
                json.append("      },\n");
            }

            if (block.lightEmission() > 0) {
                json.append("      \"light_emission\": ").append(block.lightEmission()).append(",\n");
            }

            json.append(String.format("      \"friction\": %.3f,\n", block.friction()));
            json.append("      \"destructible_by_mining\": 1.0\n");

            json.append("    }");

            if (idx < total) json.append(",");
            json.append("\n");

            mappingCount++;

            if (idx % 25 == 0) {
                LOGGER.info("AutoBridge/Mappings: Generated {} of {} block mappings...", idx, total);
            }
        }

        json.append("  }\n");
        json.append("}");

        Files.writeString(outputFile, json.toString());
        LOGGER.info("AutoBridge/Mappings: Wrote blocks.json with {} entries → {}", total, outputFile);

        return outputFile;
    }

    private String jsonKey(String value) {
        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String input) {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    public int getMappingCount() {
        return mappingCount;
    }
}
