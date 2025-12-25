package com.fabianoley.dynmaprailways.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import com.fabianoley.dynmaprailways.DynmapRailways;
import java.util.*;
import java.util.logging.Logger;
import java.lang.reflect.Method;

/**
 * Integration layer for CoreProtect API.
 * Used to retrieve rail block placements by players.
 */
public class CoreProtectIntegration {
    
    private static final Logger logger = Logger.getLogger("DynmapRailways");
    private Object coreProtectAPI;
    private DynmapRailways plugin;
    private boolean isAvailable = false;
    
    public CoreProtectIntegration(DynmapRailways plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Initialize CoreProtect integration.
     */
    public boolean initialize() {
        try {
            Plugin cp = Bukkit.getPluginManager().getPlugin("CoreProtect");
            if (cp == null) {
                logger.warning("CoreProtect plugin not found.");
                return false;
            }
            
            // Get API using reflection
            Method getAPI = cp.getClass().getMethod("getAPI");
            coreProtectAPI = getAPI.invoke(cp);
            
            if (coreProtectAPI == null) {
                logger.warning("CoreProtect API is null.");
                return false;
            }
            
            // Check if enabled
            Method isEnabled = coreProtectAPI.getClass().getMethod("isEnabled");
            Boolean enabled = (Boolean) isEnabled.invoke(coreProtectAPI);
            
            if (!enabled) {
                logger.warning("CoreProtect API is disabled.");
                return false;
            }
            
            isAvailable = true;
            logger.info("CoreProtect API initialized successfully.");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to initialize CoreProtect: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Query CoreProtect for rail block placements.
     * @param world World name to search in
     * @param maxResults Maximum number of results to return
     * @return List of rail block placement records
     */
    public List<CoreProtectRecord> getRailPlacements(String world, int maxResults) {
        if (!isAvailable || coreProtectAPI == null) {
            return Collections.emptyList();
        }
        
        List<CoreProtectRecord> results = new ArrayList<>();
        
        try {
            // This would use CoreProtect's performQuery method
            // Implementation depends on the exact CoreProtect API version
            logger.info("Rail placement query support pending CoreProtect API implementation.");
        } catch (Exception e) {
            logger.warning("Error querying CoreProtect: " + e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Check if a block type is a rail.
     */
    private boolean isRailBlock(String type) {
        return type != null && (
                type.contains("RAIL") || 
                type.contains("rail") ||
                type.equalsIgnoreCase("minecraft:rail") ||
                type.equalsIgnoreCase("minecraft:powered_rail") ||
                type.equalsIgnoreCase("minecraft:detector_rail") ||
                type.equalsIgnoreCase("minecraft:activator_rail")
        );
    }
    
    public boolean isAvailable() {
        return isAvailable;
    }
    
    /**
     * Record from CoreProtect query.
     */
    public static class CoreProtectRecord {
        public final String player;
        public final int x, y, z;
        public final String blockType;
        
        public CoreProtectRecord(String player, int x, int y, int z, String blockType) {
            this.player = player;
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockType = blockType;
        }
    }
}
