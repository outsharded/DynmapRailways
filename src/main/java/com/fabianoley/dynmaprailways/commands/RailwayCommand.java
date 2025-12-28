package com.fabianoley.dynmaprailways.commands;

import com.fabianoley.dynmaprailways.DynmapRailways;
import com.fabianoley.dynmaprailways.rail.RailLine;
import com.fabianoley.dynmaprailways.station.Station;
import com.fabianoley.dynmaprailways.scan.RailScanner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import java.util.*;

/**
 * Command handler for railway commands.
 */
public class RailwayCommand implements CommandExecutor, TabCompleter {
    
    private DynmapRailways plugin;
    
    public RailwayCommand(DynmapRailways plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "scan":
                return handleScan(sender, args);
            case "list":
                return handleList(sender);
            case "station":
                return handleStation(sender, args);
            case "reload":
                return handleReload(sender);
            case "debug":
                return handleDebug(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }
    
    private boolean handleScan(CommandSender sender, String[] args) {
        if (!sender.hasPermission("railway.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        // Optional: radius overload for targeted chunk scanning around the player
        if (args.length >= 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cRadius scan can only be used by a player.");
                return true;
            }
            int radius;
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cInvalid radius. Usage: /railway scan <chunk-radius>");
                return true;
            }
            if (radius < 0) {
                sender.sendMessage("§cRadius must be zero or positive.");
                return true;
            }

            Player player = (Player) sender;
            org.bukkit.World world = player.getWorld();
            org.bukkit.Chunk playerChunk = player.getLocation().getChunk();
            int baseX = playerChunk.getX();
            int baseZ = playerChunk.getZ();

            // Pre-load existing generated chunks within radius on the main thread (non-generating)
            sender.sendMessage("§eScanning chunks within radius " + radius + " around you...");
            final java.util.Set<org.bukkit.Chunk> chunksToScan = new java.util.LinkedHashSet<>();
            for (int cx = baseX - radius; cx <= baseX + radius; cx++) {
                for (int cz = baseZ - radius; cz <= baseZ + radius; cz++) {
                    // If already loaded, include; else try to load without generating new chunks
                    if (world.isChunkLoaded(cx, cz)) {
                        chunksToScan.add(world.getChunkAt(cx, cz));
                    } else {
                        boolean loaded = world.loadChunk(cx, cz, false); // do not generate
                        if (loaded) {
                            chunksToScan.add(world.getChunkAt(cx, cz));
                        }
                    }
                }
            }

            // Run scan asynchronously for these chunks only
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    // Get existing lines
                    List<RailLine> existingLines = new ArrayList<>(plugin.getDataStorage().getRailLines().values());
                    
                    // Scan for new lines
                    List<RailLine> newLines = RailScanner.scanChunks(world, chunksToScan);
                    
                    // Merge with existing lines to prevent duplicates
                    List<RailLine> mergedLines = RailScanner.mergeWithExistingLines(world, newLines, existingLines);
                    
                    // Replace all lines with merged result
                    plugin.getDataStorage().replaceAllRailLines(mergedLines);
                    
                    final int lineCount = mergedLines.size();
                    int humanCount = 0;
                    for (RailLine line : mergedLines) {
                        if (line.getCreatedBy() != null && !line.getCreatedBy().isEmpty()) {
                            humanCount++;
                        }
                    }
                    final int finalHumanCount = humanCount;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getMapRenderer().updateAllMarkers();
                        sender.sendMessage("§aRadius scan complete! Total " + lineCount + " rail lines (§b" + finalHumanCount + " player-placed§a).");
                        boolean playerOnly = plugin.getConfig().getBoolean("coreprotect.player-placed-only", false);
                        if (playerOnly) {
                            sender.sendMessage("§7Note: Rendering is set to player-placed lines only.");
                        }
                    });
                } catch (Exception e) {
                    sender.sendMessage("§cError during radius scan: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            return true;
        }

        // Default: scan all currently loaded chunks in all worlds
        sender.sendMessage("§eScanning all worlds for rail blocks...");
        
        // Scan all worlds asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Get all existing lines
                List<RailLine> existingLines = new ArrayList<>(plugin.getDataStorage().getRailLines().values());
                List<RailLine> allMergedLines = new ArrayList<>();
                
                int humanLines = 0;
                
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    // Scan this world
                    List<RailLine> newLines = RailScanner.scanWorld(world);
                    
                    // Filter existing lines for this world
                    List<RailLine> existingForWorld = new ArrayList<>();
                    for (RailLine line : existingLines) {
                        boolean isInWorld = false;
                        for (RailLine.RailBlock block : line.getBlocks()) {
                            if (block.world.equals(world.getName())) {
                                isInWorld = true;
                                break;
                            }
                        }
                        if (isInWorld) {
                            existingForWorld.add(line);
                        }
                    }
                    
                    // Merge new lines with existing for this world
                    List<RailLine> mergedForWorld = RailScanner.mergeWithExistingLines(world, newLines, existingForWorld);
                    
                    for (RailLine line : mergedForWorld) {
                        if (line.getCreatedBy() != null && !line.getCreatedBy().isEmpty()) {
                            humanLines++;
                        }
                    }
                    
                    allMergedLines.addAll(mergedForWorld);
                }
                
                // Also add lines from worlds that weren't scanned
                for (RailLine line : existingLines) {
                    boolean isFromScannedWorld = false;
                    for (RailLine.RailBlock block : line.getBlocks()) {
                        for (org.bukkit.World world : Bukkit.getWorlds()) {
                            if (block.world.equals(world.getName())) {
                                isFromScannedWorld = true;
                                break;
                            }
                        }
                        if (isFromScannedWorld) break;
                    }
                    if (!isFromScannedWorld) {
                        allMergedLines.add(line);
                    }
                }
                
                // Replace all lines with merged result
                plugin.getDataStorage().replaceAllRailLines(allMergedLines);
                
                // Update map on main thread
                final int finalTotalLines = allMergedLines.size();
                final int finalHumanLines = humanLines;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aScanning complete! Total " + finalTotalLines + " rail lines (§b" + finalHumanLines + " player-placed§a).");
                    boolean playerOnly = plugin.getConfig().getBoolean("coreprotect.player-placed-only", false);
                    if (playerOnly) {
                        sender.sendMessage("§7Note: Rendering is set to player-placed lines only.");
                    }
                });
            } catch (Exception e) {
                sender.sendMessage("§cError during scan: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        return true;
    }
    
    private boolean handleList(CommandSender sender) {
        try {
            Map<String, RailLine> lines = plugin.getDataStorage().getRailLines();
            sender.sendMessage("§6Rail Lines (" + lines.size() + "):");
            
            if (lines.isEmpty()) {
                sender.sendMessage("§7No rail lines detected. Use /railway scan to detect them.");
                return true;
            }
            
            for (RailLine line : lines.values()) {
                if (line.isActive()) {
                    sender.sendMessage("§e" + line.getName() + " §7(" + line.getBlockCount() + " blocks)");
                }
            }
        } catch (Exception e) {
            sender.sendMessage("§cError listing rail lines: " + e.getMessage());
        }
        return true;
    }
    
    private boolean handleStation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /railway station <create|list|remove> [station-name]");
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command requires player context.");
            return true;
        }
        
        Player player = (Player) sender;
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "create":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /railway station create <name>");
                    return true;
                }
                
                String stationName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                String stationId = stationName.toLowerCase().replace(" ", "_");
                int x = (int) player.getLocation().getX();
                int z = (int) player.getLocation().getZ();
                
                try {
                    String world = player.getWorld().getName();
                    Station station = new Station(stationId, stationName, x, player.getLocation().getBlockY(), z, world);
                    station.setCreatedBy(player.getName());
                    plugin.getDataStorage().saveStation(station);
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aStation created: " + stationName + " at (" + x + ", " + z + ") in world " + world);
                } catch (Exception e) {
                    sender.sendMessage("§cError creating station: " + e.getMessage());
                }
                return true;
                
            case "list":
                try {
                    Map<String, Station> stations = plugin.getDataStorage().getStations();
                    sender.sendMessage("§6Stations (" + stations.size() + "):");
                    for (Station s : stations.values()) {
                        sender.sendMessage("§e" + s.getName() + " §7at (" + s.getX() + ", " + s.getZ() + ")");
                    }
                } catch (Exception e) {
                    sender.sendMessage("§cError listing stations: " + e.getMessage());
                }
                return true;
                
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /railway station remove <name>");
                    return true;
                }
                
                String removeName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                String removeId = removeName.toLowerCase().replace(" ", "_");
                
                try {
                    plugin.getDataStorage().removeStation(removeId);
                    plugin.getMapRenderer().updateAllMarkers();
                    sender.sendMessage("§aStation removed: " + removeName);
                } catch (Exception e) {
                    sender.sendMessage("§cError removing station: " + e.getMessage());
                }
                return true;
                
            default:
                sender.sendMessage("§cUnknown action: " + action);
                return true;
        }
    }
    
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("railway.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        try {
            plugin.getMapRenderer().updateAllMarkers();
            sender.sendMessage("§aMap reloaded.");
        } catch (Exception e) {
            sender.sendMessage("§cError reloading: " + e.getMessage());
        }
        return true;
    }
    
    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("railway.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /railway debug <reinit|info|block>");
            return true;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "reinit":
                sender.sendMessage("§eForcing marker set reinitialization...");
                plugin.getMapRenderer().forceReinitialize();
                sender.sendMessage("§aReinitialization complete. Check console for details.");
                return true;
                
            case "info":
                sender.sendMessage("§6Debug Info:");
                sender.sendMessage("§eRail Lines: " + plugin.getDataStorage().getRailLines().size());
                sender.sendMessage("§eStations: " + plugin.getDataStorage().getStations().size());
                sender.sendMessage("§eCheck console for marker set details.");
                return true;
            
            // ========== TEMPORARY DEBUG COMMAND - REMOVE AFTER TESTING ==========
            case "block":
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: /railway debug block <x> <y> <z>");
                    return true;
                }
                try {
                    int x = Integer.parseInt(args[2]);
                    int y = Integer.parseInt(args[3]);
                    int z = Integer.parseInt(args[4]);
                    
                    org.bukkit.World world;
                    if (sender instanceof Player) {
                        world = ((Player) sender).getWorld();
                    } else {
                        world = Bukkit.getWorlds().get(0); // Default to first world
                    }
                    
                    org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                    sender.sendMessage("§6=== Block Debug at (" + x + ", " + y + ", " + z + ") ===");
                    sender.sendMessage("§eMaterial: §f" + block.getType().name());
                    sender.sendMessage("§eIs Rail Block: §f" + (block.getBlockData() instanceof org.bukkit.block.data.Rail));
                    
                    if (block.getBlockData() instanceof org.bukkit.block.data.Rail) {
                        org.bukkit.block.data.Rail rail = (org.bukkit.block.data.Rail) block.getBlockData();
                        sender.sendMessage("§eRail Shape: §f" + rail.getShape().name());
                        sender.sendMessage("§eRail Type: §f" + block.getType().name());
                    }
                    
                    sender.sendMessage("§eChunk: §f(" + (x >> 4) + ", " + (z >> 4) + ")");
                    sender.sendMessage("§eChunk Loaded: §f" + world.isChunkLoaded(x >> 4, z >> 4));
                    
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid coordinates. Use integer values.");
                }
                return true;
            // =====================================================================
                
            default:
                sender.sendMessage("§cUnknown debug action: " + action);
                return true;
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6Railway Commands:");
        sender.sendMessage("§e/railway scan §7- Scan worlds for rail blocks (admin)");
        sender.sendMessage("§e/railway list §7- List detected rail lines");
        sender.sendMessage("§e/railway station create <name> §7- Create a station at your location");
        sender.sendMessage("§e/railway station list §7- List all stations");
        sender.sendMessage("§e/railway station remove <name> §7- Remove a station");
        sender.sendMessage("§e/railway reload §7- Reload map visualization (admin)");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("scan", "list", "station", "reload");
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("station")) {
            return Arrays.asList("create", "list", "remove");
        }
        
        return Collections.emptyList();
    }
}

