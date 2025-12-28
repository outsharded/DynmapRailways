package com.fabianoley.dynmaprailways.map;

import com.fabianoley.dynmaprailways.DynmapRailways;
import com.fabianoley.dynmaprailways.rail.RailLine;
import com.fabianoley.dynmaprailways.rail.RailLine.RailBlock;
import com.fabianoley.dynmaprailways.station.Station;
import com.fabianoley.dynmaprailways.storage.RailwayDataStorage;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PolyLineMarker;
import org.dynmap.markers.CircleMarker;
import java.util.*;
import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Renders railway lines and stations on Dynmap as markers.
 */
public class RailwayMapRenderer {

    private static final Logger logger = Logger.getLogger("DynmapRailways");
    private static final String MARKER_SET_ID = "railway-lines";
    private static final String STATIONS_SET_ID = "railway-stations";

    private DynmapRailways plugin;
    private DynmapCommonAPI dynmapAPI;
    private RailwayDataStorage dataStorage;
    private MarkerAPI markerAPI;
    private MarkerSet railwayMarkerSet;
    private MarkerSet stationMarkerSet;

    public RailwayMapRenderer(DynmapRailways plugin, DynmapCommonAPI dynmapAPI, RailwayDataStorage dataStorage) {
        this.plugin = plugin;
        this.dynmapAPI = dynmapAPI;
        this.dataStorage = dataStorage;
        // No CoreProtect filtering
    }
    
    /**
     * Check if debug mode is enabled.
     */
    private boolean isDebugEnabled() {
        return plugin.getConfig().getBoolean("general.debug", false);
    }
    
    /**
     * Initialize the map renderer.
     */
    public void initialize() {
        if (isDebugEnabled()) {
            logger.info("[DEBUG] Starting RailwayMapRenderer initialization...");
        }
        // Touch plugin to avoid unused field warning and for debugging context
        if (plugin != null) {
            if (isDebugEnabled()) {
                logger.fine("[DEBUG] Renderer initialized for plugin: " + plugin.getClass().getName());
            }
        }

        if (dynmapAPI == null) {
            logger.severe("[DEBUG] Dynmap API is null!");
            return;
        }
        if (isDebugEnabled()) {
            logger.info("[DEBUG] Dynmap API found: " + dynmapAPI.getClass().getName());
        }

        // Get Marker API
        markerAPI = dynmapAPI.getMarkerAPI();
        if (isDebugEnabled()) {
            logger.info("[DEBUG] Marker API retrieved: " + (markerAPI != null ? markerAPI.getClass().getName() : "null"));
        }

        if (markerAPI == null) {
            logger.severe("[DEBUG] Dynmap Marker API not available!");
            return;
        }

        // Get or create marker sets
        if (isDebugEnabled()) {
            logger.info("[DEBUG] Creating marker sets...");
        }
        createMarkerSets();

        if (isDebugEnabled()) {
            logger.info("[DEBUG] Railway marker set: " + (railwayMarkerSet != null ? railwayMarkerSet.getClass().getName() : "null"));
            logger.info("[DEBUG] Station marker set: " + (stationMarkerSet != null ? stationMarkerSet.getClass().getName() : "null"));
        }

        if (railwayMarkerSet == null || stationMarkerSet == null) {
            if (isDebugEnabled()) {
                logger.warning("[DEBUG] Failed to create marker sets");
            }
            return;
        }

        // Initial render
        if (isDebugEnabled()) {
            logger.info("[DEBUG] Updating all markers...");
        }
        updateAllMarkers();

        logger.info("Railway map renderer initialized successfully.");
    }
    
    /**
     * Create or get marker sets.
     */
    private void createMarkerSets() {
        if (isDebugEnabled()) {
            logger.info("[DEBUG] createMarkerSets() called");
        }
        // Get marker sets
        railwayMarkerSet = markerAPI.getMarkerSet(MARKER_SET_ID);
        stationMarkerSet = markerAPI.getMarkerSet(STATIONS_SET_ID);
        if (isDebugEnabled()) {
            logger.info("[DEBUG] Retrieved existing marker sets: railway=" + (railwayMarkerSet != null) + ", station=" + (stationMarkerSet != null));
        }

        // Create if missing
        if (railwayMarkerSet == null) {
            if (isDebugEnabled()) {
                logger.info("[DEBUG] Railway marker set is null, attempting to create...");
            }
            railwayMarkerSet = markerAPI.createMarkerSet(MARKER_SET_ID, "Railway Lines", null, false);
            if (isDebugEnabled()) {
                logger.info("[DEBUG] Created Railway Lines marker set successfully");
            }
        }

        if (stationMarkerSet == null) {
            if (isDebugEnabled()) {
                logger.info("[DEBUG] Station marker set is null, attempting to create...");
            }
            stationMarkerSet = markerAPI.createMarkerSet(STATIONS_SET_ID, "Railway Stations", null, false);
            if (isDebugEnabled()) {
                logger.info("[DEBUG] Created Railway Stations marker set successfully");
            }
        }
        
        // Set layer priorities - higher priority renders on top
        // Lines at priority 5, stations at priority 10 so stations render over lines
        if (railwayMarkerSet != null) {
            railwayMarkerSet.setLayerPriority(5);
        }
        if (stationMarkerSet != null) {
            stationMarkerSet.setLayerPriority(10);
        }
    }
    
    /**
     * Update all markers on the map.
     */
    public void updateAllMarkers() {
        try {
            if (isDebugEnabled()) {
                logger.info("[DEBUG] updateAllMarkers() called");
            }
            
            if (railwayMarkerSet == null || stationMarkerSet == null) {
                if (isDebugEnabled()) {
                    logger.warning("[DEBUG] Marker sets not available - cannot render");
                }
                return;
            }
            
            // Ensure layer priorities are set - higher priority renders on top
            // This needs to be set every time to persist across zoom/pan operations
            railwayMarkerSet.setLayerPriority(5);
            stationMarkerSet.setLayerPriority(10);
            
            // Clear old markers
            clearMarkers(railwayMarkerSet);
            clearMarkers(stationMarkerSet);
            if (isDebugEnabled()) {
                logger.info("[DEBUG] Cleared old markers");
            }
            
            // Render rail lines
            Map<String, RailLine> railLines = dataStorage.getRailLines();
            if (isDebugEnabled()) {
                logger.info("[DEBUG] Found " + railLines.size() + " rail lines to render");
            }
            
            boolean playerPlacedOnly = plugin.getConfig().getBoolean("coreprotect.player-placed-only", true);
            for (RailLine line : railLines.values()) {
                if (isDebugEnabled()) {
                    logger.info("[DEBUG] Processing line: " + line.getId() + " with " + line.getBlockCount() + " blocks, active=" + line.isActive());
                }
                if (line.isActive() && line.getBlockCount() > 1 && (!playerPlacedOnly || line.getCreatedBy() != null)) {
                    renderRailLine(line);
                    if (isDebugEnabled()) {
                        logger.info("[DEBUG] Rendered line: " + line.getId());
                    }
                }
            }
            
            // Render stations
            Map<String, Station> stations = dataStorage.getStations();
            if (isDebugEnabled()) {
                logger.info("[DEBUG] Found " + stations.size() + " stations to render");
            }
            
            for (Station station : stations.values()) {
                if (isDebugEnabled()) {
                    logger.info("[DEBUG] Processing station: " + station.getId() + ", active=" + station.isActive());
                }
                if (station.isActive()) {
                    renderStation(station);
                    if (isDebugEnabled()) {
                        logger.info("[DEBUG] Rendered station: " + station.getId());
                    }
                }
            }
            
            if (isDebugEnabled()) {
                logger.info("[DEBUG] updateAllMarkers() completed successfully");
            }
        } catch (Exception e) {
            logger.warning("Error updating markers: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Clear all markers from a set.
     */
    private void clearMarkers(Object markerSet) {
        try {
            Method deleteMarkers = markerSet.getClass().getMethod("deleteMarkers", Collection.class);
            Method getMarkers = markerSet.getClass().getMethod("getMarkers");
            Collection<?> markers = (Collection<?>) getMarkers.invoke(markerSet);
            if (!markers.isEmpty()) {
                deleteMarkers.invoke(markerSet, markers);
            }
        } catch (Exception e) {
            logger.fine("Could not clear markers: " + e.getMessage());
        }
    }
    
    /**
     * Render a rail line as polylines connecting blocks.
     */
    private void renderRailLine(RailLine line) {
        Set<RailBlock> blocks = line.getBlocks();
        if (blocks == null) {
            logger.warning("[renderRailLine] blocks is null for line: " + line.getId());
            return;
        }
        if (blocks.size() < 2) {
            logger.fine("[renderRailLine] Not enough blocks to render line: " + line.getId());
            return;
        }

        // --- Path-walking logic: order blocks as a path ---
        List<RailBlock> ordered = walkRailPath(blocks);
        if (ordered.size() < 2) {
            logger.fine("[renderRailLine] Ordered path too short: " + line.getId());
            return;
        }

        // --- Use all ordered points to ensure continuous connection across curves and slopes ---
        List<RailBlock> polylinePoints = new ArrayList<>();
        RailBlock prev = null;
        int lastDx = 0, lastDz = 0;
        for (RailBlock block : ordered) {
            if (prev == null) {
                polylinePoints.add(block);
            } else {
                int dx = Integer.compare(block.x, prev.x); // -1, 0, or 1
                int dz = Integer.compare(block.z, prev.z); // -1, 0, or 1
                // Add point only if direction changes (avoids drawing diagonals between non-adjacent blocks)
                if (dx != lastDx || dz != lastDz) {
                    polylinePoints.add(prev);
                    lastDx = dx;
                    lastDz = dz;
                }
            }
            prev = block;
        }
        // Always add the final point
        if (prev != null && (polylinePoints.isEmpty() || !polylinePoints.get(polylinePoints.size() - 1).equals(prev))) {
            polylinePoints.add(prev);
        }

        double[] xpts = new double[polylinePoints.size()];
        double[] ypts = new double[polylinePoints.size()];
        double[] zpts = new double[polylinePoints.size()];

        for (int i = 0; i < polylinePoints.size(); i++) {
            RailBlock block = polylinePoints.get(i);
            if (block == null) {
                logger.warning("[renderRailLine] Null RailBlock in polylinePoints for line: " + line.getId());
                return;
            }
            xpts[i] = block.x + 0.5;
            ypts[i] = 64.0; // Flat 2D map, fixed Y (ground level)
            zpts[i] = block.z + 0.5;
        }

        // Use the world from the first block
        String world = polylinePoints.get(0).world;
        if (world == null) {
            logger.warning("[renderRailLine] World is null for line: " + line.getId());
            return;
        }

        // Remove any existing marker with this ID
        PolyLineMarker oldMarker = null;
        try {
            oldMarker = railwayMarkerSet.findPolyLineMarker(line.getId());
        } catch (Exception e) {
            logger.warning("[renderRailLine] Exception in findPolyLineMarker: " + e.getMessage());
        }
        if (oldMarker != null) {
            try {
                oldMarker.deleteMarker();
            } catch (Exception e) {
                logger.warning("[renderRailLine] Exception in deleteMarker: " + e.getMessage());
            }
        }


        PolyLineMarker marker = null;
        try {
            marker = railwayMarkerSet.createPolyLineMarker(
                line.getId(), line.getName(), false, world,
                xpts, ypts, zpts, false
            );
        } catch (Exception e) {
            logger.warning("[renderRailLine] Exception in createPolyLineMarker: " + e.getMessage());
        }
        if (marker != null) {
            try {
                // Get line appearance settings from config (with defaults)
                int lineWidth = plugin.getConfig().getInt("lines.width", 3);
                double lineOpacity = plugin.getConfig().getDouble("lines.opacity", 1.0);
                
                marker.setLineStyle(lineWidth, lineOpacity, parseColorToInt(line.getColor()));
            } catch (Exception e) {
                logger.warning("[renderRailLine] Exception in setLineStyle: " + e.getMessage());
            }
            logger.fine("Rendered rail line: " + line.getName() + " (" + line.getBlockCount() + " blocks)");
        } else {
            logger.warning("[renderRailLine] Failed to create PolyLineMarker for line: " + line.getId());
        }
        // ...existing code...

    }

    

    /**
     * Walk the rail cluster as a path, returning an ordered list of blocks.
     * This finds an endpoint and does a DFS walk.
     */
    private List<RailBlock> walkRailPath(Set<RailBlock> blocks) {
        if (blocks.isEmpty()) return Collections.emptyList();
        // Build adjacency map with vertical fallback (y±1); no diagonals to keep paths axis-aligned
        Map<RailBlock, List<RailBlock>> adj = new HashMap<>();
        for (RailBlock b : blocks) {
            List<RailBlock> neighbors = new ArrayList<>();
            int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
            for (int[] d : dirs) {
                int nx = b.x + d[0];
                int nz = b.z + d[1];
                // Try same Y first
                RailBlock nSame = new RailBlock(nx, b.y, nz, b.world);
                if (blocks.contains(nSame)) {
                    neighbors.add(nSame);
                    continue;
                }
                // If not found, try one block above (ascending)
                RailBlock nUp = new RailBlock(nx, b.y + 1, nz, b.world);
                if (blocks.contains(nUp)) {
                    neighbors.add(nUp);
                    continue;
                }
                // If still not found, try one block below (descending)
                RailBlock nDown = new RailBlock(nx, b.y - 1, nz, b.world);
                if (blocks.contains(nDown)) {
                    neighbors.add(nDown);
                }
            }
            adj.put(b, neighbors);
        }
        // Find endpoint (degree 1), or any block
        RailBlock start = null;
        for (RailBlock b : blocks) {
            if (adj.get(b).size() == 1) { start = b; break; }
        }
        if (start == null) start = blocks.iterator().next();
        // DFS walk
        List<RailBlock> path = new ArrayList<>();
        Set<RailBlock> visited = new HashSet<>();
        dfsWalk(start, null, adj, visited, path);
        return path;
    }

    private void dfsWalk(RailBlock curr, RailBlock parent, Map<RailBlock, List<RailBlock>> adj, Set<RailBlock> visited, List<RailBlock> path) {
        visited.add(curr);
        path.add(curr);
        for (RailBlock n : adj.get(curr)) {
            if (!n.equals(parent) && !visited.contains(n)) {
                dfsWalk(n, curr, adj, visited, path);
            }
        }
    }
    
    /**
     * Render a station.
     */
    private void renderStation(Station station) {
        // Remove any existing marker with this ID
        CircleMarker oldMarker = stationMarkerSet.findCircleMarker(station.getId());
        if (oldMarker != null) {
            oldMarker.deleteMarker();
        }

        // Use correct Dynmap API signature for createCircleMarker
        // (String id, String label, boolean markup, String world, double x, double y, double z, double radiusx, double radiusz, boolean persistent)
        String world = station.getWorld();
        if (world == null) {
            logger.warning("[renderStation] Station world is null for station: " + station.getId());
            return;
        }
        double x = station.getX() + 0.5;
        double z = station.getZ() + 0.5;
        double y = station.getY() > 0 ? station.getY() + 0.5 : 64.0;
        
        // Get station appearance settings from config (with defaults)
        double radius = plugin.getConfig().getDouble("stations.radius", 5.0);
        String fillColorHex = plugin.getConfig().getString("stations.fill-color", "#FFFFFF");
        double fillOpacity = plugin.getConfig().getDouble("stations.fill-opacity", 0.3);
        int borderWidth = plugin.getConfig().getInt("stations.border-width", 2);
        String borderColorHex = plugin.getConfig().getString("stations.border-color", "#000000");
        double borderOpacity = plugin.getConfig().getDouble("stations.border-opacity", 1.0);
        
        boolean persistent = false;
        CircleMarker marker = stationMarkerSet.createCircleMarker(
            station.getId(), station.getName(), false, world,
            x, y, z, radius, radius, persistent
        );
        if (marker != null) {
            marker.setFillStyle(fillOpacity, parseColorToInt(fillColorHex));
            marker.setLineStyle(borderWidth, borderOpacity, parseColorToInt(borderColorHex));
            logger.fine("Rendered station: " + station.getName());
        }
    }
    
    /**
     * Convert hex color to int.
     */
    private int parseColorToInt(String hexColor) {
        try {
            hexColor = hexColor.replace("#", "");
            return Integer.parseInt(hexColor, 16);
        } catch (Exception e) {
            return 0x000000;
        }
    }
    
    /**
     * Force re-initialization of marker sets (debug method).
     */
    public void forceReinitialize() {
        logger.info("[DEBUG] forceReinitialize() called - clearing and reinitializing marker sets");
        railwayMarkerSet = null;
        stationMarkerSet = null;
        try {
            createMarkerSets();
            logger.info("[DEBUG] After createMarkerSets: railway=" + (railwayMarkerSet != null) + ", station=" + (stationMarkerSet != null));
            if (railwayMarkerSet != null && stationMarkerSet != null) {
                updateAllMarkers();
                logger.info("Markers reinitialized successfully!");
            } else {
                logger.warning("Marker sets are still null after reinitialization");
            }
        } catch (Exception e) {
            logger.warning("Error during force reinitialization: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        // Cleanup
    }
}

