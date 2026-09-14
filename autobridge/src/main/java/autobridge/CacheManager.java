package autobridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CacheManager — Persists scan results between server restarts.
 */
public class CacheManager {

    private static final String CACHE_FILE = "autobridge_cache.json";
    private static final String MOD_HASH_FILE = "mod_hashes.json";

    private final Path cacheDir;
    private final Map<String, String> currentModHashes = new ConcurrentHashMap<>();

    public CacheManager(Path cacheDir) {
        this.cacheDir = cacheDir;
        try { Files.createDirectories(cacheDir); } catch (IOException ignored) {}
    }

    public void registerModHash(String namespace, String hash) {
        currentModHashes.put(namespace, hash);
    }

    public CachedScanData loadCache() {
        Path cacheFile = cacheDir.resolve(CACHE_FILE);
        Path hashFile = cacheDir.resolve(MOD_HASH_FILE);

        if (!Files.exists(cacheFile) || !Files.exists(hashFile)) {
            return null;
        }

        try {
            String storedHashes = Files.readString(hashFile);
            String currentHashes = buildHashString();

            if (!storedHashes.equals(currentHashes)) {
                System.out.println("[CacheManager] Mod list changed — cache invalid");
                return null;
            }

            System.out.println("[CacheManager] Cache loaded successfully");
            return new CachedScanData(true);
        } catch (IOException e) {
            return null;
        }
    }

    public void saveCache(List<ModScanner.ModItem> items, List<ModScanner.ModBlock> blocks) {
        try {
            Files.writeString(cacheDir.resolve(MOD_HASH_FILE), buildHashString());

            String cacheContent = "{\"items_count\":" + items.size()
                + ",\"blocks_count\":" + blocks.size()
                + ",\"cached\":true}";
            Files.writeString(cacheDir.resolve(CACHE_FILE), cacheContent);

            System.out.println("[CacheManager] Saved cache — " + items.size() + " items, " + blocks.size() + " blocks");
        } catch (IOException e) {
            System.err.println("[CacheManager] Failed to save cache: " + e.getMessage());
        }
    }

    public void clearCache() {
        try {
            Files.deleteIfExists(cacheDir.resolve(CACHE_FILE));
            Files.deleteIfExists(cacheDir.resolve(MOD_HASH_FILE));
        } catch (IOException ignored) {}
    }

    private String buildHashString() {
        return currentModHashes.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + e.getValue())
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }

    public record CachedScanData(boolean isValid) {}
}
