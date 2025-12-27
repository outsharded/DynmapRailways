package com.fabianoley.dynmaprailways;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.Bukkit;
import com.fabianoley.dynmaprailways.map.RailwayMapRenderer;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import com.fabianoley.dynmaprailways.storage.RailwayDataStorage;
import com.fabianoley.dynmaprailways.commands.RailwayCommand;
import java.io.File;
import java.util.logging.Logger;

/**
 * Main plugin class for DynmapRailways addon.
 * Integrates with Dynmap to display rail lines as a tube map overlay.
 */
public class DynmapRailways extends JavaPlugin {
    
    private static DynmapRailways instance;
    private static final Logger logger = Logger.getLogger("DynmapRailways");
    
    private DynmapCommonAPI dynmapAPI; // Will be set at runtime
    private RailwayDataStorage dataStorage;
    private RailwayMapRenderer mapRenderer;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Create config directory if it doesn't exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        
        // Load configuration
        saveDefaultConfig();
        
        // Initialize Dynmap asynchronously via API listener
        DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
            @Override
            public void apiEnabled(DynmapCommonAPI api) {
                dynmapAPI = api;
                getLogger().info("Dynmap API initialized successfully.");

                // Initialize data storage
                try {
                    dataStorage = new RailwayDataStorage(DynmapRailways.this);
                    dataStorage.initialize();
                    getLogger().info("Data storage initialized.");
                } catch (Exception e) {
                    getLogger().severe("Failed to initialize data storage: " + e.getMessage());
                    getServer().getPluginManager().disablePlugin(DynmapRailways.this);
                    return;
                }



                // Initialize map renderer
                getLogger().info("[DEBUG] About to initialize map renderer...");
                mapRenderer = new RailwayMapRenderer(DynmapRailways.this, dynmapAPI, dataStorage);
                getLogger().info("[DEBUG] RailwayMapRenderer created, calling initialize()...");
                mapRenderer.initialize();
                getLogger().info("[DEBUG] Railway map renderer initialization complete.");

                // Register commands
                registerCommands();

                // Register event listeners
                registerListeners();

                getLogger().info("DynmapRailways v" + getDescription().getVersion() + " enabled!");
            }
        });
    }
    
    @Override
    public void onDisable() {
        if (mapRenderer != null) {
            mapRenderer.shutdown();
        }
        
        if (dataStorage != null) {
            dataStorage.shutdown();
        }
        
        getLogger().info("DynmapRailways disabled.");
    }
    
    /**
     * Initialize the Dynmap API.
     */
    // Dynmap API initialization is now handled via DynmapCommonAPIListener
    
    /**
     * Register plugin commands.
     */
    private void registerCommands() {
        RailwayCommand railwayCommand = new RailwayCommand(this);
        getCommand("railway").setExecutor(railwayCommand);
        getCommand("railway").setTabCompleter(railwayCommand);
    }
    
    /**
     * Register event listeners.
     */
    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        // Event listeners will be added as needed
    }
    
    public static DynmapRailways getInstance() {
        return instance;
    }
    
    public DynmapCommonAPI getDynmapAPI() {
        return dynmapAPI;
    }
    
    public RailwayDataStorage getDataStorage() {
        return dataStorage;
    }
    
    public RailwayMapRenderer getMapRenderer() {
        return mapRenderer;
    }
}
