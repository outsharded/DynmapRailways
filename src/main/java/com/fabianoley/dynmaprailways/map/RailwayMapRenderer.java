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
    }
    
    /**
     * Initialize the map renderer.
     */
    public void initialize() {
        logger.info("[DEBUG] Starting RailwayMapRenderer initialization...");

        if (dynmapAPI == null) {
            logger.severe("[DEBUG] Dynmap API is null!");
            return;
        }
        logger.info("[DEBUG] Dynmap API found: " + dynmapAPI.getClass().getName());

        // Get Marker API
        markerAPI = dynmapAPI.getMarkerAPI();
        logger.info("[DEBUG] Marker API retrieved: " + (markerAPI != null ? markerAPI.getClass().getName() : "null"));

        if (markerAPI == null) {
            logger.severe("[DEBUG] Dynmap Marker API not available!");
            return;
        }

        // Get or create marker sets
        logger.info("[DEBUG] Creating marker sets...");
        createMarkerSets();

        logger.info("[DEBUG] Railway marker set: " + (railwayMarkerSet != null ? railwayMarkerSet.getClass().getName() : "null"));
        logger.info("[DEBUG] Station marker set: " + (stationMarkerSet != null ? stationMarkerSet.getClass().getName() : "null"));

        if (railwayMarkerSet == null || stationMarkerSet == null) {
            logger.warning("[DEBUG] Failed to create marker sets");
            return;
        }

        // Initial render
        logger.info("[DEBUG] Updating all markers...");
        updateAllMarkers();

        logger.info("Railway map renderer initialized successfully.");
    }
    
    /**
     * Create or get marker sets.
     */
    private void createMarkerSets() {
        logger.info("[DEBUG] createMarkerSets() called");
        // Get marker sets
        railwayMarkerSet = markerAPI.getMarkerSet(MARKER_SET_ID);
        stationMarkerSet = markerAPI.getMarkerSet(STATIONS_SET_ID);
        logger.info("[DEBUG] Retrieved existing marker sets: railway=" + (railwayMarkerSet != null) + ", station=" + (stationMarkerSet != null));

        // Create if missing
        if (railwayMarkerSet == null) {
            logger.info("[DEBUG] Railway marker set is null, attempting to create...");
            railwayMarkerSet = markerAPI.createMarkerSet(MARKER_SET_ID, "Railway Lines", null, false);
            logger.info("[DEBUG] Created Railway Lines marker set successfully");
        }

        if (stationMarkerSet == null) {
            logger.info("[DEBUG] Station marker set is null, attempting to create...");
            stationMarkerSet = markerAPI.createMarkerSet(STATIONS_SET_ID, "Railway Stations", null, false);
            logger.info("[DEBUG] Created Railway Stations marker set successfully");
        }
    }
    
    /**
     * Update all markers on the map.
     */
    public void updateAllMarkers() {
        try {
            logger.info("[DEBUG] updateAllMarkers() called");
            
            if (railwayMarkerSet == null || stationMarkerSet == null) {
                logger.warning("[DEBUG] Marker sets not available - cannot render");
                return;
            }
            
            // Clear old markers
            clearMarkers(railwayMarkerSet);
            clearMarkers(stationMarkerSet);
            logger.info("[DEBUG] Cleared old markers");
            
            // Render rail lines
            Map<String, RailLine> railLines = dataStorage.getRailLines();
            logger.info("[DEBUG] Found " + railLines.size() + " rail lines to render");
            
            for (RailLine line : railLines.values()) {
                logger.info("[DEBUG] Processing line: " + line.getId() + " with " + line.getBlockCount() + " blocks, active=" + line.isActive());
                if (line.isActive() && line.getBlockCount() > 1) {
                    renderRailLine(line);
                    logger.info("[DEBUG] Rendered line: " + line.getId());
                }
            }
            
            // Render stations
            Map<String, Station> stations = dataStorage.getStations();
            logger.info("[DEBUG] Found " + stations.size() + " stations to render");
            
            for (Station station : stations.values()) {
                logger.info("[DEBUG] Processing station: " + station.getId() + ", active=" + station.isActive());
                if (station.isActive()) {
                    renderStation(station);
                    logger.info("[DEBUG] Rendered station: " + station.getId());
                }
            }
            
            logger.info("[DEBUG] updateAllMarkers() completed successfully");
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
        if (line == null) {
            logger.warning("[renderRailLine] line is null");
            return;
        }
        if (railwayMarkerSet == null) {
            logger.warning("[renderRailLine] railwayMarkerSet is null");
            return;
        }
        Set<RailBlock> blocks = line.getBlocks();
        if (blocks == null) {
            logger.warning("[renderRailLine] blocks is null for line: " + line.getId());
            return;
        }
        if (blocks.size() < 2) {
            logger.fine("[renderRailLine] Not enough blocks to render line: " + line.getId());
            return;
        }

        // Convert to arrays for polyline
        List<RailBlock> blockList = new ArrayList<>(blocks);
        blockList.sort((a, b) -> {
            if (a.x != b.x) return Integer.compare(a.x, b.x);
            if (a.z != b.z) return Integer.compare(a.z, b.z);
            return Integer.compare(a.y, b.y);
        });

        double[] xpts = new double[blockList.size()];
        double[] ypts = new double[blockList.size()];
        double[] zpts = new double[blockList.size()];

        for (int i = 0; i < blockList.size(); i++) {
            RailBlock block = blockList.get(i);
            if (block == null) {
                logger.warning("[renderRailLine] Null RailBlock in blockList for line: " + line.getId());
                return;
            }
            xpts[i] = block.x + 0.5;
            ypts[i] = block.y + 0.5;
            zpts[i] = block.z + 0.5;
        }

        // Use the world from the first block
        String world = blockList.get(0).world;
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
                marker.setLineStyle(3, 1.0, parseColorToInt(line.getColor()));
            } catch (Exception e) {
                logger.warning("[renderRailLine] Exception in setLineStyle: " + e.getMessage());
            }
            logger.fine("Rendered rail line: " + line.getName() + " (" + blocks.size() + " blocks)");
        } else {
            logger.warning("[renderRailLine] Failed to create PolyLineMarker for line: " + line.getId());
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
        // (String id, String label, boolean markup, String world, double x, double y, double z, double radius, double ytop, boolean persistent)
        String world = null;
        double x = station.getX() + 0.5;
        double z = station.getZ() + 0.5;
        double y = 64.0; // Default Y, or fetch from station if available
        double radius = 10.0;
        double ytop = y + 2.0;
        boolean persistent = false;
        CircleMarker marker = stationMarkerSet.createCircleMarker(
            station.getId(), station.getName(), false, world,
            x, y, z, radius, ytop, persistent
        );
        if (marker != null) {
            marker.setFillStyle(0.5, 0xFFFFFF);
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

