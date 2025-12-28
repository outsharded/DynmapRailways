package com.fabianoley.dynmaprailways.storage;

import com.fabianoley.dynmaprailways.DynmapRailways;
import com.fabianoley.dynmaprailways.rail.RailLine;
import com.fabianoley.dynmaprailways.station.Station;
import com.google.gson.*;
import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles storage and retrieval of railway data.
 * Supports both file-based and Dynmap database storage.
 */
public class RailwayDataStorage {
    
    private static final Logger logger = Logger.getLogger("DynmapRailways");
    private static final String RAILS_FILE = "rails.json";
    private static final String STATIONS_FILE = "stations.json";
    
   // private DynmapRailways plugin;
    private File dataFolder;
    private Map<String, RailLine> railLines = new HashMap<>();
    private Map<String, Station> stations = new HashMap<>();
    private Gson gson;
    
    public RailwayDataStorage(DynmapRailways plugin) {
        //this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }
    
    /**
     * Initialize storage system.
     */
    public void initialize() throws Exception {
        ensureDataFolder();
        loadAllData();
        logger.info("Railway data storage initialized with " + 
                   railLines.size() + " lines and " + 
                   stations.size() + " stations.");
    }
    
    /**
     * Ensure data folder exists.
     */
    private void ensureDataFolder() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
    }
    
    /**
     * Load all data from storage.
     */
    private void loadAllData() throws Exception {
        loadRailLines();
        loadStations();
    }
    
    /**
     * Load rail lines from file.
     */
    private void loadRailLines() throws Exception {
        File file = new File(dataFolder, RAILS_FILE);
        if (!file.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement element : array) {
                RailLine line = gson.fromJson(element, RailLine.class);
                railLines.put(line.getId(), line);
            }
            logger.info("Loaded " + railLines.size() + " rail lines.");
        }
    }
    
    /**
     * Load stations from file.
     */
    private void loadStations() throws Exception {
        File file = new File(dataFolder, STATIONS_FILE);
        if (!file.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(file)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement element : array) {
                Station station = gson.fromJson(element, Station.class);
                stations.put(station.getId(), station);
            }
            logger.info("Loaded " + stations.size() + " stations.");
        }
    }
    
    /**
     * Save a rail line.
     */
    public void saveRailLine(RailLine line) throws Exception {
        railLines.put(line.getId(), line);
        saveRailLines();
    }
    
    /**
     * Save all rail lines.
     */
    private void saveRailLines() throws Exception {
        File file = new File(dataFolder, RAILS_FILE);
        JsonArray array = new JsonArray();
        for (RailLine line : railLines.values()) {
            array.add(gson.toJsonTree(line));
        }
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(gson.toJson(array));
        }
    }
    
    /**
     * Save a station.
     */
    public void saveStation(Station station) throws Exception {
        stations.put(station.getId(), station);
        saveStations();
    }
    
    /**
     * Save all stations.
     */
    private void saveStations() throws Exception {
        File file = new File(dataFolder, STATIONS_FILE);
        JsonArray array = new JsonArray();
        for (Station station : stations.values()) {
            array.add(gson.toJsonTree(station));
        }
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(gson.toJson(array));
        }
    }
    
    /**
     * Get all rail lines.
     */
    public Map<String, RailLine> getRailLines() {
        return new HashMap<>(railLines);
    }
    
    /**
     * Get a specific rail line.
     */
    public RailLine getRailLine(String id) {
        return railLines.get(id);
    }
    
    /**
     * Remove a rail line.
     */
    public void removeRailLine(String id) throws Exception {
        railLines.remove(id);
        saveRailLines();
    }
    
    /**
     * Get all stations.
     */
    public Map<String, Station> getStations() {
        return new HashMap<>(stations);
    }
    
    /**
     * Get a specific station.
     */
    public Station getStation(String id) {
        return stations.get(id);
    }
    
    /**
     * Remove a station.
     */
    public void removeStation(String id) throws Exception {
        stations.remove(id);
        saveStations();
    }
    
    /**
     * Replace all rail lines with a new set.
     */
    public void replaceAllRailLines(List<RailLine> newLines) throws Exception {
        railLines.clear();
        for (RailLine line : newLines) {
            railLines.put(line.getId(), line);
        }
        saveRailLines();
        logger.info("Replaced all rail lines. Now storing " + railLines.size() + " lines.");
    }
    
    /**
     * Clear all data.
     */
    public void clearAll() throws Exception {
        railLines.clear();
        stations.clear();
        saveRailLines();
        saveStations();
    }
    
    /**
     * Shutdown storage system.
     */
    public void shutdown() {
        // Flush any pending writes if needed
        try {
            saveRailLines();
            saveStations();
        } catch (Exception e) {
            logger.warning("Error saving data on shutdown: " + e.getMessage());
        }
    }
}
