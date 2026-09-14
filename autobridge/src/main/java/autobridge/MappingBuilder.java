package autobridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * MappingBuilder — Generates Geyser custom_mappings JSON files.
 */
public class MappingBuilder {

    private final Path mappingsDir;
    private int mappingCount = 0;

    public MappingBuilder(Path mappingsDir) {
        this.mappingsDir = mappingsDir;
        try { Files.createDirectories(mappingsDir); } catch (IOException ignored) {}
    }

    public Path generateItemsJson(List<ModScanner.ModItem> items) throws IOException {
        Path outputFile = mappingsDir.resolve("items.json");
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"format_version\": 2,\n  \"items\": {\n");

        int total = items.size();
        int idx = 0;
        for (ModScanner.ModItem item : items) {
            idx++;
            String bedrockName = item.bedrockId();

            json.append("    \"").append(esc(item.javaId())).append("\": [\n");
            json.append("      {\n");
            json.append("        \"bedrock_identifier\": \"").append(esc(bedrockName)).append("\",\n");
            json.append("        \"definition\": { \"model\": \"").append(esc(bedrockName.replace(':', '_'))).append("\" },\n");
            json.append("        \"display_name\": \"").append(esc(item.displayName())).append("\",\n");
            json.append("        \"bedrock_options\": {\n");
            json.append("          \"icon\": \"").append(esc(bedrockName.replace(':', '_'))).append("\",\n");

            String cat = switch (item.geometryType()) {
                case EQUIPPABLE -> "equipment";
                case CONSUMABLE -> "food";
                default -> "buildingBlocks";
            };
            json.append("          \"creative_category\": \"").append(cat).append("\",\n");
            json.append("          \"creative_group\": \"").append(esc(item.namespace())).append("\"\n");
            json.append("        },\n");
            json.append("        \"components\": {\n");

            boolean hasComponents = false;
            if (item.maxStackSize() > 1) {
                json.append("          \"minecraft:max_stack_size\": ").append(item.maxStackSize());
                hasComponents = true;
            }
            if (item.isConsumable()) {
                if (hasComponents) json.append(",\n");
                json.append("          \"minecraft:consumable\": { \"consume_seconds\": 1.0, \"consuming_item_notification\": true }");
                hasComponents = true;
            }
            if (!hasComponents) {
                json.append("          \"minecraft:usable_by\": [\"player\"]");
            }
            json.append("\n        }\n      }\n    ]");
            if (idx < total) json.append(",");
            json.append("\n");
            mappingCount++;
        }

        json.append("  }\n}");
        Files.writeString(outputFile, json.toString());
        return outputFile;
    }

    public Path generateBlocksJson(List<ModScanner.ModBlock> blocks) throws IOException {
        Path outputFile = mappingsDir.resolve("blocks.json");
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"format_version\": 1,\n  \"blocks\": {\n");

        int total = blocks.size();
        int idx = 0;
        for (ModScanner.ModBlock block : blocks) {
            idx++;
            String bedrockName = block.bedrockId().replace(':', '_');

            json.append("    \"").append(esc(block.javaId())).append("\": {\n");
            json.append("      \"name\": \"").append(bedrockName).append("\",\n");
            json.append("      \"geometry\": \"minecraft:geometry.full_block\",\n");
            json.append("      \"material_instances\": {\n");
            json.append("        \"*\": { \"type\": \"texture\", \"texture\": \"").append(bedrockName).append("\" }\n");
            json.append("      },\n");

            if (block.stateCount() > 1) {
                json.append("      \"state_overrides\": {\n");
                int maxStates = Math.min(block.stateCount(), 16);
                for (int i = 0; i < maxStates; i++) {
                    json.append("        ").append(i).append(": {\n");
                    json.append("          \"name\": \"").append(bedrockName).append("_state_").append(i).append("\",\n");
                    json.append("          \"geometry\": \"minecraft:geometry.full_block\",\n");
                    json.append("          \"material_instances\": { \"*\": { \"type\": \"texture\", \"texture\": \"")
                        .append(bedrockName).append("_state_").append(i).append("\" } }\n");
                    json.append("        }");
                    if (i < maxStates - 1) json.append(",");
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
        }

        json.append("  }\n}");
        Files.writeString(outputFile, json.toString());
        return outputFile;
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public int getMappingCount() { return mappingCount; }
}
