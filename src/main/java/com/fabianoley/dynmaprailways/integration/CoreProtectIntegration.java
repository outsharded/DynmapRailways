package com.fabianoley.dynmaprailways.integration;

import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

import com.fabianoley.dynmaprailways.DynmapRailways;

/**
 * Wrapper for CoreProtect API to query block placement history.
 * Optional: runs only if CoreProtect is present and enabled.
 */
public class CoreProtectIntegration {
    private final DynmapRailways plugin;
    private Object coreProtectAPI; // use reflection to avoid compile-time dependency
    private boolean enabled = false;

    public CoreProtectIntegration(DynmapRailways plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        try {
            Plugin coreProtectPlugin = plugin.getServer().getPluginManager().getPlugin("CoreProtect");
            if (coreProtectPlugin == null || !coreProtectPlugin.isEnabled()) {
                plugin.getLogger().warning("CoreProtect not available or not enabled - player filtering disabled");
                enabled = false;
                return;
            }

            // Obtain API via reflection: plugin has getAPI() returning CoreProtectAPI
            try {
                Method getAPI = coreProtectPlugin.getClass().getMethod("getAPI");
                coreProtectAPI = getAPI.invoke(coreProtectPlugin);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get CoreProtect API via reflection");
                enabled = false;
                return;
            }

            if (coreProtectAPI == null) {
                plugin.getLogger().warning("CoreProtect API was null - player filtering disabled");
                enabled = false;
                return;
            }

            try {
                Method isEnabled = coreProtectAPI.getClass().getMethod("isEnabled");
                Object res = isEnabled.invoke(coreProtectAPI);
                if (!(res instanceof Boolean) || !((Boolean) res)) {
                    plugin.getLogger().warning("CoreProtect API not enabled - player filtering disabled");
                    enabled = false;
                    return;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check CoreProtect API enabled state");
                enabled = false;
                return;
            }
            enabled = true;
            plugin.getLogger().info("CoreProtect integration enabled");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize CoreProtect integration", e);
            enabled = false;
        }
    }

    public boolean isEnabled() { return enabled; }

    /**
     * Returns the username of the player who placed the block, or null if natural/admin.
     */
    public String getBlockPlacer(Block block) {
        if (!enabled || coreProtectAPI == null) return null;
        try {
            Method blockLookup = coreProtectAPI.getClass().getMethod("blockLookup", org.bukkit.block.Block.class, int.class);
            Object result = blockLookup.invoke(coreProtectAPI, block, 0);
            @SuppressWarnings("unchecked")
            List<String[]> data = (List<String[]>) result;
            if (data != null && !data.isEmpty()) {
                String[] entry = data.get(0);
                String username = extractUsername(entry);
                if (username != null) {
                    return username;
                }
            }
            return null;
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "CoreProtect lookup failed at " + block.getLocation(), e);
            return null;
        }
    }

    private String extractUsername(String[] entry) {
        if (entry == null) return null;
        // Preferred index 0, fallback to any plausible name in the array
        for (int i = 0; i < entry.length; i++) {
            String v = entry[i];
            if (v == null) continue;
            String s = v.trim();
            if (s.isEmpty()) continue;
            // Exclude numeric-only (timestamps, ids) and special/system markers
            if (s.matches("^[0-9]+$") || s.startsWith("#") || "worldedit".equalsIgnoreCase(s)) {
                continue;
            }
            // Accept typical Minecraft name characters
            if (s.matches("^[A-Za-z0-9_]{3,16}$")) {
                return s;
            }
        }
        // As last resort, try index 0 if not excluded
        if (entry.length > 0) {
            String s = entry[0];
            if (s != null && !s.startsWith("#") && !s.matches("^[0-9]+$")) {
                return s;
            }
        }
        return null;
    }

    /**
     * Applies config-based filters for player-placed-only, age bounds, ignore list.
     */
    public boolean matchesFilters(Block block, String placer) {
        if (!enabled) return true; // No filtering if CP disabled
        if (placer == null) return false; // Natural/admin rejected if filtering enabled

        boolean playerPlacedOnly = plugin.getConfig().getBoolean("coreprotect.player-placed-only", true);
        if (!playerPlacedOnly) return true;

        int minAgeDays = plugin.getConfig().getInt("coreprotect.min-age-days", 0);
        int maxAgeDays = plugin.getConfig().getInt("coreprotect.max-age-days", 0);
        try {
            if (minAgeDays > 0 || maxAgeDays > 0) {
                Method blockLookup = coreProtectAPI.getClass().getMethod("blockLookup", org.bukkit.block.Block.class, int.class);
                Object result = blockLookup.invoke(coreProtectAPI, block, 0);
                @SuppressWarnings("unchecked")
                List<String[]> data = (List<String[]>) result;
                if (data != null && !data.isEmpty()) {
                    String[] entry = data.get(0);
                    long timestamp = Long.parseLong(entry[5]) * 1000L;
                    long ageDays = (System.currentTimeMillis() - timestamp) / (1000L * 60 * 60 * 24);
                    if (minAgeDays > 0 && ageDays < minAgeDays) return false;
                    if (maxAgeDays > 0 && ageDays > maxAgeDays) return false;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "CoreProtect age check failed", e);
        }

        List<String> ignorePlayers = plugin.getConfig().getStringList("coreprotect.ignore-players");
        if (ignorePlayers != null) {
            if (ignorePlayers.contains(placer) || ignorePlayers.contains("#" + placer.toLowerCase())) return false;
        }
        return true;
    }
}
