package autobridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * ModScanner — Reads mod JARs directly from disk to discover blocks and items.
 *
 * Does NOT require a Minecraft server or NeoForge runtime. Parses:
 * neoforge.mods.toml / META-INF/mods.toml for mod metadata,
 * assets/NAMESPACE/models/item/ for item definitions,
 * assets/NAMESPACE/models/block/ for block definitions,
 * assets/NAMESPACE/textures/ for textures.
 *
 * This makes the entire pipeline testable without a running Minecraft server.
 */
public class ModScanner {

    private static final String VANILLA_NAMESPACE = "minecraft";

    // Network ID counter — in a real server this comes from the registry
    private int nextNetworkId = 1000;

    private final Path modsDir;
    private final List<ModInfo> discoveredMods = new ArrayList<>();

    public ModScanner(Path modsDir) {
        this.modsDir = modsDir;
    }

    /**
     * Scan all JARs in the mods directory. Returns discovered items and blocks.
     */
    public ScanResult scan() {
        List<ModItem> items = new ArrayList<>();
        List<ModBlock> blocks = new ArrayList<>();

        if (!Files.exists(modsDir)) {
            System.out.println("[ModScanner] Mods directory does not exist: " + modsDir);
            return new ScanResult(items, blocks, discoveredMods);
        }

        try (Stream<Path> stream = Files.list(modsDir)) {
            List<Path> jars = stream
                .filter(p -> p.toString().endsWith(".jar"))
                .toList();

            System.out.println("[ModScanner] Found " + jars.size() + " mod JARs in " + modsDir);

            for (Path jarPath : jars) {
                try {
                    scanJar(jarPath, items, blocks);
                } catch (Exception e) {
                    System.err.println("[ModScanner] Failed to scan " + jarPath.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[ModScanner] Failed to list mods directory: " + e.getMessage());
        }

        System.out.println("[ModScanner] Scan complete: " + items.size() + " items, " + blocks.size() + " blocks from " + discoveredMods.size() + " mods");
        return new ScanResult(items, blocks, discoveredMods);
    }

    /**
     * Scan a single mod JAR for items, blocks, and textures.
     */
    private void scanJar(Path jarPath, List<ModItem> items, List<ModBlock> blocks) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Step 1: Read mod metadata
            ModInfo modInfo = readModInfo(jar);
            if (modInfo == null) {
                return; // Not a valid mod JAR
            }
            discoveredMods.add(modInfo);
            String namespace = modInfo.namespace();

            // Step 2: Discover items from model files
            List<String> itemModels = findEntries(jar, "assets/" + namespace + "/models/item/", ".json");
            for (String modelPath : itemModels) {
                String itemName = extractName(modelPath);
                if (itemName == null || itemName.equals("item")) continue;

                int networkId = nextNetworkId++;
                String texturePath = findItemTexture(jar, namespace, itemName);

                items.add(new ModItem(
                    namespace + ":" + itemName,
                    formatDisplayName(itemName),
                    networkId,
                    64, // default stack size
                    false, // not consumable by default
                    GeometryType.MODEL_2D,
                    texturePath != null ? List.of(new TextureSource(texturePath, null, TextureType.DIRECT)) : List.of()
                ));
            }

            // Step 3: Discover blocks from model files
            List<String> blockModels = findEntries(jar, "assets/" + namespace + "/models/block/", ".json");
            for (String modelPath : blockModels) {
                String blockName = extractName(modelPath);
                if (blockName == null) continue;

                String texturePath = findBlockTexture(jar, namespace, blockName);
                int lightEmission = detectLightEmission(jar, namespace, blockName);

                blocks.add(new ModBlock(
                    namespace + ":" + blockName,
                    formatDisplayName(blockName),
                    lightEmission,
                    0.6f,
                    GeometryType.CUBE,
                    texturePath != null ? List.of(new TextureSource(texturePath, null, TextureType.DIRECT)) : List.of(),
                    1 // default state count
                ));
            }

            System.out.println("[ModScanner] Scanned " + modInfo.name() + " (" + namespace + "): "
                + itemModels.size() + " items, " + blockModels.size() + " blocks");

        } catch (IOException e) {
            throw new IOException("Failed to read JAR: " + jarPath, e);
        }
    }

    /**
     * Read mod metadata from neoforge.mods.toml or META-INF/mods.toml.
     */
    private ModInfo readModInfo(JarFile jar) {
        // Try NeoForge format first
        JarEntry nfEntry = jar.getJarEntry("META-INF/neoforge.mods.toml");
        if (nfEntry != null) {
            return readTomlModInfo(jar, nfEntry);
        }

        // Try Forge format
        JarEntry fEntry = jar.getJarEntry("META-INF/mods.toml");
        if (fEntry != null) {
            return readTomlModInfo(jar, fEntry);
        }

        // Try Fabric format
        JarEntry fabEntry = jar.getJarEntry("fabric.mod.json");
        if (fabEntry != null) {
            return readFabricModInfo(jar, fabEntry);
        }

        return null;
    }

    /**
     * Parse a simple TOML-like mods.toml file for mod ID and name.
     * This is a simplified parser — handles the common case without a full TOML library.
     */
    private ModInfo readTomlModInfo(JarFile jar, JarEntry entry) {
        try (InputStream is = jar.getInputStream(entry)) {
            String content = new String(is.readAllBytes());
            String modId = extractTomlValue(content, "modId");
            String displayName = extractTomlValue(content, "displayName");
            String version = extractTomlValue(content, "version");

            if (modId == null || modId.isEmpty() || VANILLA_NAMESPACE.equals(modId)) {
                return null;
            }

            return new ModInfo(modId, displayName != null ? displayName : modId, version != null ? version : "unknown");
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Parse fabric.mod.json for mod ID and name.
     */
    private ModInfo readFabricModInfo(JarFile jar, JarEntry entry) {
        try (InputStream is = jar.getInputStream(entry)) {
            String content = new String(is.readAllBytes());
            String id = extractJsonStringValue(content, "id");
            String name = extractJsonStringValue(content, "name");
            String version = extractJsonStringValue(content, "version");

            if (id == null || id.isEmpty() || VANILLA_NAMESPACE.equals(id)) {
                return null;
            }

            return new ModInfo(id, name != null ? name : id, version != null ? version : "unknown");
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Find all entries in a JAR under a given path prefix with a given suffix.
     */
    private List<String> findEntries(JarFile jar, String prefix, String suffix) {
        List<String> entries = new ArrayList<>();
        var jarEntries = jar.entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry entry = jarEntries.nextElement();
            String name = entry.getName();
            if (name.startsWith(prefix) && name.endsWith(suffix) && !entry.isDirectory()) {
                entries.add(name);
            }
        }
        return entries;
    }

    /**
     * Extract the item/block name from a model file path.
     * e.g., "assets/ae2/models/item/certus_quartz.json" → "certus_quartz"
     */
    private String extractName(String path) {
        int lastSlash = path.lastIndexOf('/');
        int dotIdx = path.lastIndexOf('.');
        if (lastSlash < 0 || dotIdx < 0 || dotIdx <= lastSlash) return null;
        return path.substring(lastSlash + 1, dotIdx);
    }

    /**
     * Find the texture path for an item by checking common locations.
     */
    private String findItemTexture(JarFile jar, String namespace, String itemName) {
        String[] candidates = {
            "assets/" + namespace + "/textures/items/" + itemName + ".png",
            "assets/" + namespace + "/textures/item/" + itemName + ".png"
        };
        for (String c : candidates) {
            if (jar.getEntry(c) != null) return c;
        }
        return null;
    }

    /**
     * Find the texture path for a block.
     */
    private String findBlockTexture(JarFile jar, String namespace, String blockName) {
        String[] candidates = {
            "assets/" + namespace + "/textures/block/" + blockName + ".png",
            "assets/" + namespace + "/textures/blocks/" + blockName + ".png"
        };
        for (String c : candidates) {
            if (jar.getEntry(c) != null) return c;
        }
        return null;
    }

    /**
     * Detect light emission from blockstate JSON (simplified heuristic).
     */
    private int detectLightEmission(JarFile jar, String namespace, String blockName) {
        // Check if the block model references a "glow" or "light" texture
        String modelPath = "assets/" + namespace + "/models/block/" + blockName + ".json";
        JarEntry entry = jar.getJarEntry(modelPath);
        if (entry == null) return 0;

        try (InputStream is = jar.getInputStream(entry)) {
            String content = new String(is.readAllBytes()).toLowerCase();
            if (content.contains("glow") || content.contains("light") || content.contains("emissive")) {
                return 7; // moderate light
            }
        } catch (IOException ignored) {}
        return 0;
    }

    /**
     * Extract a value from a simple TOML string.
     * Looks for patterns like: modId = "value" or modId='value'
     */
    private String extractTomlValue(String toml, String key) {
        // Simple regex-free extraction
        String pattern1 = key + " = \"";
        String pattern2 = key + "=\"";
        String pattern3 = key + " = '";

        for (String pattern : new String[]{pattern1, pattern2, pattern3}) {
            int idx = toml.indexOf(pattern);
            if (idx >= 0) {
                int start = idx + pattern.length();
                char quote = pattern.charAt(pattern.length() - 1);
                int end = toml.indexOf(quote, start);
                if (end > start) {
                    return toml.substring(start, end);
                }
            }
        }
        return null;
    }

    /**
     * Extract a string value from JSON.
     */
    private String extractJsonStringValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;

        // Find the colon and then the string value
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;

        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) return null;

        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return null;

        return json.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * Format a snake_case name into a display name.
     */
    private String formatDisplayName(String snakeCase) {
        String[] parts = snakeCase.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    // ==================== Data Records ====================

    public record ModInfo(String namespace, String name, String version) {}

    public record ModItem(
        String javaId,
        String displayName,
        int javaNetworkId,
        int maxStackSize,
        boolean isConsumable,
        GeometryType geometryType,
        List<TextureSource> textures
    ) {
        public String bedrockId() {
            String ns = javaId.substring(0, javaId.indexOf(':'));
            String path = javaId.substring(javaId.indexOf(':') + 1);
            String safeName = path.replace('/', '_').replace('-', '_') + "_ab_item";
            return ns + ":" + safeName;
        }

        public String namespace() {
            return javaId.substring(0, javaId.indexOf(':'));
        }

        public String path() {
            return javaId.substring(javaId.indexOf(':') + 1);
        }
    }

    public record ModBlock(
        String javaId,
        String displayName,
        int lightEmission,
        float friction,
        GeometryType geometryType,
        List<TextureSource> textures,
        int stateCount
    ) {
        public String bedrockId() {
            String ns = javaId.substring(0, javaId.indexOf(':'));
            String path = javaId.substring(javaId.indexOf(':') + 1);
            String safeName = path.replace('/', '_').replace('-', '_') + "_ab_block";
            return ns + ":" + safeName;
        }

        public String namespace() {
            return javaId.substring(0, javaId.indexOf(':'));
        }

        public String path() {
            return javaId.substring(javaId.indexOf(':') + 1);
        }
    }

    public record TextureSource(String name, Path sourcePath, TextureType type) {}

    public record ScanResult(List<ModItem> items, List<ModBlock> blocks, List<ModInfo> mods) {}

    public enum GeometryType {
        MODEL_2D, MODEL_3D, EQUIPPABLE, CONSUMABLE, CUBE, COMPLEX
    }

    public enum TextureType {
        DIRECT, NAMESPACE_SCAN, FALLBACK
    }
}
